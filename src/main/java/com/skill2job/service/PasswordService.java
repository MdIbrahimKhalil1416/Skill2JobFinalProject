package com.skill2job.service;

import com.skill2job.audit.AuditService;
import com.skill2job.dto.AuthResponse;
import com.skill2job.dto.PasswordDtos;
import com.skill2job.dto.UserResponse;
import com.skill2job.entity.PasswordResetCode;
import com.skill2job.entity.User;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.PasswordResetCodeRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.security.AuthUser;
import com.skill2job.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * "Forgot password" (emailed 6-digit code, same safeguards as the signup OTP: BCrypt-hashed,
 * expiring, single use, limited attempts, resend cooldown) and "change password" for a signed-in
 * user. Either one stamps User.passwordChangedAt, which makes JwtAuthenticationFilter reject every
 * token issued earlier - i.e. other sessions are signed out.
 */
@Service
public class PasswordService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Same answer whether or not the email exists, so the endpoint cannot be used to discover accounts. */
    public static final String GENERIC_SENT = "If an account exists for that email, we've sent a 6-digit code to it.";

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final JwtUtil jwtUtil;
    private final CodeStore codeStore;

    @Value("${app.password-reset.expiry-minutes:15}")
    private int expiryMinutes;
    @Value("${app.otp.resend-cooldown-seconds:60}")
    private int cooldownSeconds;
    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    public PasswordService(UserRepository userRepository, PasswordResetCodeRepository codeRepository,
                           PasswordEncoder passwordEncoder, EmailService emailService,
                           NotificationService notificationService, AuditService auditService, JwtUtil jwtUtil,
                           CodeStore codeStore) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.jwtUtil = jwtUtil;
        this.codeStore = codeStore;
    }

    public PasswordDtos.MessageResponse forgot(PasswordDtos.ForgotPasswordRequest req) {
        User user = userRepository.findByEmailIgnoreCase(AuthService.normalizeEmail(req.email())).orElse(null);
        if (user == null || !user.isActive() || !user.isEmailVerified()) {
            return new PasswordDtos.MessageResponse(GENERIC_SENT);
        }
        PasswordResetCode doc = codeRepository.findByUserId(user.getId()).orElseGet(PasswordResetCode::new);
        if (doc.getLastSentAt() != null && Instant.now().isBefore(doc.getLastSentAt().plusSeconds(cooldownSeconds))) {
            // Silently keep the code already sent: telling the caller would reveal that the account exists.
            return new PasswordDtos.MessageResponse(GENERIC_SENT);
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        doc.setUserId(user.getId());
        doc.setEmail(user.getEmail());
        doc.setCodeHash(passwordEncoder.encode(code));
        doc.setExpiresAt(Instant.now().plus(Duration.ofMinutes(expiryMinutes)));
        doc.setUsed(false);
        doc.setAttempts(0);
        doc.setLastSentAt(Instant.now());
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(Instant.now());
        }
        doc = codeRepository.save(doc);
        boolean sent;
        try {
            sent = emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), code, expiryMinutes);
        } catch (RuntimeException e) {
            sent = false;
        }
        if (!sent && !emailService.devConsoleFallback()) {
            // Nothing reached the inbox: don't make the user wait out a cooldown for a code that never arrived.
            doc.setLastSentAt(null);
            codeRepository.save(doc);
        }
        auditService.record(null, "PASSWORD_RESET_REQUESTED", "USER", user.getId(), user.getEmail(), true, null);
        return new PasswordDtos.MessageResponse(GENERIC_SENT);
    }

    public PasswordDtos.MessageResponse reset(PasswordDtos.ResetPasswordRequest req) {
        PasswordRules.check(req.newPassword());
        User user = userRepository.findByEmailIgnoreCase(AuthService.normalizeEmail(req.email())).orElse(null);
        PasswordResetCode doc = user == null ? null : codeRepository.findByUserId(user.getId()).orElse(null);
        // One message for every failure (unknown email, no code, wrong, expired, used, too many attempts).
        CodeVerifier.verify(codeStore, passwordEncoder, PasswordResetCode.class, doc, req.code(), maxAttempts, Instant.now());
        setPassword(user, req.newPassword());
        auditService.record(null, "PASSWORD_RESET", "USER", user.getId(), user.getEmail(), true, null);
        return new PasswordDtos.MessageResponse("Your password has been reset. You can log in with the new password.");
    }

    /** Returns a fresh token so the session that made the change stays signed in. */
    public AuthResponse change(AuthUser current, PasswordDtos.ChangePasswordRequest req) {
        User user = userRepository.findById(current.id()).orElseThrow(() -> ApiException.notFound("User not found."));
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            auditService.record(current, "PASSWORD_CHANGE", "USER", user.getId(), null, false, "Wrong current password");
            throw ApiException.badRequest("Your current password is not correct.");
        }
        PasswordRules.check(req.newPassword());
        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw ApiException.badRequest("The new password must be different from the current one.");
        }
        setPassword(user, req.newPassword());
        notificationService.notify(user.getId(), "Your password was changed. Other sessions were signed out.",
                NotificationLinks.settings());
        auditService.record(current, "PASSWORD_CHANGE", "USER", user.getId(), null, true, null);
        // Issued strictly after passwordChangedAt (rounded down to seconds) so this token stays valid.
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new AuthResponse(token, jwtUtil.getExpirationMs(), UserResponse.from(user));
    }

    private void setPassword(User user, String raw) {
        user.setPassword(passwordEncoder.encode(raw));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);
        emailService.sendPasswordChangedEmail(user.getEmail(), user.getName());
    }
}
