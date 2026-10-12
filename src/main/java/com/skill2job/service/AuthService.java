package com.skill2job.service;

import com.skill2job.audit.AuditService;
import com.skill2job.dto.AuthResponse;
import com.skill2job.dto.LoginRequest;
import com.skill2job.dto.RegisterRequest;
import com.skill2job.dto.RegisterResponse;
import com.skill2job.dto.ResendOtpRequest;
import com.skill2job.dto.ResendOtpResponse;
import com.skill2job.dto.UserResponse;
import com.skill2job.dto.VerifyOtpRequest;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.User;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.security.AuthUser;
import com.skill2job.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    static final String RESEND_OK = "If a signup is waiting for that email, a new code has been sent to it.";

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    /**
     * Email verification (signup OTP). Off by default (app.auth.email-verification / EMAIL_VERIFICATION_ENABLED):
     * then a new account is active at once and the user is logged in straight after signing up, so no email
     * provider is needed. With it on, the OTP flow below applies unchanged. (The Java default stays true so
     * code that builds this service by hand keeps the original, stricter behaviour.)
     */
    @Value("${app.auth.email-verification:false}")
    private boolean emailVerificationEnabled = true;

    public AuthService(UserRepository userRepository,
                       StudentProfileRepository studentProfileRepository,
                       EmployerProfileRepository employerProfileRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
                       OtpService otpService, FileStorageService fileStorageService, AuditService auditService) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.fileStorageService = fileStorageService;
        this.auditService = auditService;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Students and employers can sign up. The account is created right away but stays unverified
     * (emailVerified=false, cannot log in) until the OTP sent here is confirmed through verifyOtp().
     * An optional CV (students only) is validated and stored before the OTP is sent, so a rejected
     * file never leaves behind an unverified account with no way to retry the upload.
     */
    public RegisterResponse register(RegisterRequest req, MultipartFile cv) {
        if (req.role() == Role.ADMIN) {
            throw ApiException.badRequest("You can only register as a student or an employer.");
        }
        String email = normalizeEmail(req.email());
        PasswordRules.check(req.password());
        User existing = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (existing != null && (existing.isEmailVerified() || existing.getRole() == Role.ADMIN)) {
            throw ApiException.conflict("An account with this email already exists.");
        }
        if (req.role() == Role.EMPLOYER && (req.companyName() == null || req.companyName().isBlank())) {
            throw ApiException.badRequest("Company name is required for employers.");
        }
        if (cv != null && !cv.isEmpty() && req.role() != Role.STUDENT) {
            throw ApiException.badRequest("Only students can upload a CV.");
        }
        if (existing != null && emailVerificationEnabled) {
            // A signup for this email is still waiting for its code. Within the resend cooldown nothing changes
            // (so a second request cannot swap the password under a code that is already in the owner's inbox).
            otpService.assertCooldownOver(existing);
        }

        // The CV is validated and stored BEFORE the account exists, so a rejected file never leaves an
        // unverified account behind (which would block retrying with the same email).
        String storedCv = null;
        String cvName = null;
        if (cv != null && !cv.isEmpty()) {
            storedCv = fileStorageService.store(cv);
            cvName = FileStorageService.displayName(cv);
        }

        User user;
        String replacedCv = null;
        try {
            if (existing == null) {
                // Created unverified: createUser() (used by the admin bootstrap and the demo seeder) keeps
                // defaulting to verified=true, so this calls the internal overload explicitly instead.
                user = createUser(req.name().trim(), email, req.password(), req.role(), !emailVerificationEnabled);
            } else {
                // Re-registration of an UNVERIFIED email: the newest signup replaces the old unverified one,
                // so nobody can squat an address (or pre-set its password) before its real owner arrives.
                replacedCv = studentProfileRepository.findByUserId(existing.getId())
                        .map(StudentProfile::getCvStoredFilename).orElse(null);
                studentProfileRepository.findByUserId(existing.getId()).ifPresent(studentProfileRepository::delete);
                employerProfileRepository.findByUserId(existing.getId()).ifPresent(employerProfileRepository::delete);
                existing.setName(req.name().trim());
                existing.setPassword(passwordEncoder.encode(req.password()));
                existing.setRole(req.role());
                existing.setEmailVerified(!emailVerificationEnabled);
                user = userRepository.save(existing);
            }
            createEmptyProfile(user, req.companyName());
            if (storedCv != null) {
                StudentProfile profile = studentProfileRepository.findByUserId(user.getId())
                        .orElseThrow(() -> ApiException.serverError("Could not attach the CV to your profile."));
                profile.setCvOriginalFilename(cvName);
                profile.setCvStoredFilename(storedCv);
                profile.setCvUploadedAt(Instant.now());
                studentProfileRepository.save(profile);
            }
        } catch (RuntimeException e) {
            if (storedCv != null) {
                fileStorageService.delete(storedCv);   // nothing references it - don't leave it orphaned
            }
            throw e;
        }
        if (replacedCv != null && !replacedCv.equals(storedCv)) {
            fileStorageService.delete(replacedCv);
        }

        if (!emailVerificationEnabled) {
            // No email step: the account is active and the user is signed in right away.
            return new RegisterResponse(user.getId(), user.getEmail(), "Account created. Welcome to Skill2Job!", 0L, false,
                    buildAuthResponse(user));
        }

        // Account exists from here on. A failed email must never undo it: the user can request the code again.
        boolean sent = otpService.generateAndSend(user);
        String message = sent
                ? "Account created. Enter the 6-digit code we sent to your email to finish signing up."
                : "Account created, but we could not send the verification email right now. "
                  + "Use \"Resend code\" on the next page in a moment.";
        return new RegisterResponse(user.getId(), user.getEmail(), message, otpService.getExpiryMinutes() * 60L, sent, null);
    }

    /** Also used by the startup seeder and the admin account bootstrap: those are trusted, pre-verified. */
    public User createUser(String name, String email, String rawPassword, Role role) {
        return createUser(name, email, rawPassword, role, true);
    }

    private User createUser(String name, String email, String rawPassword, Role role, boolean emailVerified) {
        User user = new User();
        user.setName(name);
        user.setEmail(normalizeEmail(email));
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(emailVerified);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    public void createEmptyProfile(User user, String companyName) {
        if (user.getRole() == Role.STUDENT) {
            StudentProfile p = new StudentProfile();
            p.setUserId(user.getId());
            p.setFullName(user.getName());
            studentProfileRepository.save(p);
        } else if (user.getRole() == Role.EMPLOYER) {
            EmployerProfile p = new EmployerProfile();
            p.setUserId(user.getId());
            p.setCompanyName(companyName == null ? user.getName() : companyName.trim());
            employerProfileRepository.save(p);
        }
    }

    /** Confirms the OTP and, on success, activates the account and logs the user in. */
    public AuthResponse verifyOtp(VerifyOtpRequest req) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(req.email())).orElse(null);
        if (user == null || user.isEmailVerified()) {
            // Same answer (and same work) as a wrong code: this endpoint must not reveal which emails have accounts.
            otpService.verify("-", req.otp());
            throw ApiException.badRequest(CodeVerifier.INVALID);
        }
        otpService.verify(user.getId(), req.otp());
        user.setEmailVerified(true);
        user = userRepository.save(user);
        return buildAuthResponse(user);
    }

    /**
     * Sends a new code for a pending signup, replacing the previous one. Unknown and already-verified emails get
     * the same success-looking answer, so this cannot be used to discover accounts.
     */
    public ResendOtpResponse resendOtp(ResendOtpRequest req) {
        long expiry = otpService.getExpiryMinutes() * 60L;
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(req.email())).orElse(null);
        if (user == null || user.isEmailVerified()) {
            return new ResendOtpResponse(RESEND_OK, expiry, true);
        }
        boolean sent = otpService.resend(user);
        return sent ? new ResendOtpResponse(RESEND_OK, expiry, true)
                : new ResendOtpResponse("We could not send the email right now. Please try again in a moment.", expiry, false);
    }

    public AuthResponse login(LoginRequest req) {
        String email = normalizeEmail(req.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            // Spend the same BCrypt time as a real check so response time does not reveal unknown emails.
            CodeVerifier.burn(passwordEncoder, req.password());
        }
        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            // Audited without the password; the email shows repeated failed attempts against one account.
            auditService.record(null, "LOGIN_FAILED", "USER", user == null ? null : user.getId(), email, false, "Wrong email or password");
            throw ApiException.unauthorized("Wrong email or password.");
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("Your account has been deactivated. Contact the admin.");
        }
        if (!user.isEmailVerified() && !emailVerificationEnabled) {
            // Signed up while verification was on and never finished it: with verification now off, a correct
            // password is enough, so the account is activated instead of being locked out forever.
            user.setEmailVerified(true);
            user = userRepository.save(user);
        }
        if (!user.isEmailVerified()) {
            throw ApiException.forbidden(
                    "Please verify your email before logging in. Check your inbox for the code, "
                    + "or request a new one from the verification page.");
        }
        auditService.record(new AuthUser(user.getId(), user.getEmail(), user.getName(), user.getRole()),
                "LOGIN", "USER", user.getId(), null, true, null);
        return buildAuthResponse(user);
    }

    public UserResponse me(String userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found."));
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, jwtUtil.getExpirationMs(), UserResponse.from(user));
    }
}
