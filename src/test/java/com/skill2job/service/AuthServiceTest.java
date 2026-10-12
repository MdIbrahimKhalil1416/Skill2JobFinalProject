package com.skill2job.service;

import com.skill2job.audit.AuditService;
import com.skill2job.dto.AuthResponse;
import com.skill2job.dto.LoginRequest;
import com.skill2job.dto.RegisterRequest;
import com.skill2job.dto.RegisterResponse;
import com.skill2job.dto.ResendOtpRequest;
import com.skill2job.dto.ResendOtpResponse;
import com.skill2job.dto.VerifyOtpRequest;
import com.skill2job.entity.CodeDoc;
import com.skill2job.entity.EmailOtp;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.User;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.EmailOtpRepository;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.security.AuthUser;
import com.skill2job.security.JwtUtil;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Registration, verification, resend and login with a mocked mail sender (nothing is ever emailed) and in-memory
 * repositories. Covers the signup flows end to end at the service level.
 */
class AuthServiceTest {

    static class QuietAudit extends AuditService {
        QuietAudit() { super(null, null); }
        @Override public void record(String a, String b, String c, String d, boolean e, String f) { }
        @Override public void record(AuthUser u, String a, String b, String c, String d, boolean e, String f) { }
    }

    static class FakeJwt extends JwtUtil {
        FakeJwt() { super("0123456789012345678901234567890123456789", 1000); }
        @Override public String generateToken(String userId, String email, Role role) { return "token-for-" + userId; }
        @Override public long getExpirationMs() { return 1000; }
    }

    static final class Fixture {
        final Map<String, Object> users = new LinkedHashMap<>();
        final Map<String, Object> students = new LinkedHashMap<>();
        final Map<String, Object> employers = new LinkedHashMap<>();
        final Map<String, Object> otps = new LinkedHashMap<>();
        final UserRepository userRepo = MapRepo.of(UserRepository.class, users);
        final StudentProfileRepository studentRepo = MapRepo.of(StudentProfileRepository.class, students);
        final EmployerProfileRepository employerRepo = MapRepo.of(EmployerProfileRepository.class, employers);
        final EmailOtpRepository otpRepo = MapRepo.of(EmailOtpRepository.class, otps);
        final RecordingEmailService mail = new RecordingEmailService();
        final Path cvDir;
        final FileStorageService storage;
        final AuthService auth;
        final OtpService otp;

        Fixture() {
            try {
                cvDir = Files.createTempDirectory("s2j-auth-cv");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            storage = new FileStorageService(cvDir.toString(), 5);
            otp = new OtpService(otpRepo, OtpServiceTest.encoder(), mail, new InMemoryCodeStore(id -> (CodeDoc) otps.get(id)));
            OtpServiceTest.set(otp, "expiryMinutes", 5);
            OtpServiceTest.set(otp, "resendCooldownSeconds", 60);
            OtpServiceTest.set(otp, "maxAttempts", 5);
            auth = new AuthService(userRepo, studentRepo, employerRepo, OtpServiceTest.encoder(), new FakeJwt(), otp, storage, new QuietAudit());
        }

        RegisterResponse registerStudent(String email, String pw) {
            return auth.register(new RegisterRequest("Sara", email, pw, Role.STUDENT, null), null);
        }

        User user(String email) { return userRepo.findByEmailIgnoreCase(email).get(); }
        void expireCooldown(String email) { otpRepo.findByUserId(user(email).getId()).get().setLastSentAt(Instant.now().minusSeconds(120)); }
        String code(String email) { return mail.lastCode(email, "otp"); }
        long fileCount() throws IOException { try (Stream<Path> s = Files.list(cvDir)) { return s.count(); } }
    }

    static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    // ---------------------------------------------------------------- registration

    // ---- email verification turned OFF (the default in application.properties) ----

    private static Fixture withoutEmailVerification() {
        Fixture f = new Fixture();
        OtpServiceTest.set(f.auth, "emailVerificationEnabled", false);
        return f;
    }

    @Test
    void withVerificationOffSignupIsActiveAndLoggedInWithoutAnyEmail() {
        Fixture f = withoutEmailVerification();
        RegisterResponse r = f.registerStudent("Sara@Example.com", "secret12");
        assertNotNull(r.auth(), "the response carries a login");
        assertNotNull(r.auth().token());
        assertTrue(f.user("sara@example.com").isEmailVerified());
        assertTrue(f.mail.sent.isEmpty(), "no OTP email is sent");
        assertTrue(f.otps.isEmpty(), "no code is stored");
        assertEquals(200, status(() -> f.auth.login(new LoginRequest("sara@example.com", "secret12"))));
    }

    @Test
    void withVerificationOffAVerifiedEmailStillCannotBeRegisteredTwice() {
        Fixture f = withoutEmailVerification();
        f.registerStudent("sara@example.com", "secret12");
        assertEquals(409, status(() -> f.registerStudent("sara@example.com", "other999")));
        assertEquals(401, status(() -> f.auth.login(new LoginRequest("sara@example.com", "other999"))), "password unchanged");
    }

    @Test
    void anOldUnverifiedSignupCanLogInOnceVerificationIsTurnedOff() {
        Fixture f = new Fixture();                       // signed up while verification was ON
        f.registerStudent("sara@example.com", "secret12");
        assertFalse(f.user("sara@example.com").isEmailVerified());
        OtpServiceTest.set(f.auth, "emailVerificationEnabled", false);
        assertEquals(401, status(() -> f.auth.login(new LoginRequest("sara@example.com", "wrong999"))), "still needs the password");
        assertFalse(f.user("sara@example.com").isEmailVerified());
        assertEquals(200, status(() -> f.auth.login(new LoginRequest("sara@example.com", "secret12"))));
        assertTrue(f.user("sara@example.com").isEmailVerified());
    }

    @Test
    void withVerificationOnRegistrationReturnsNoLogin() {
        RegisterResponse r = new Fixture().registerStudent("sara@example.com", "secret12");
        assertNull(r.auth());
    }


    @Test
    void studentRegistrationCreatesAnUnverifiedAccountAndEmailsACodeWithoutALogin() {
        Fixture f = new Fixture();
        RegisterResponse r = f.registerStudent("Sara@Example.com", "secret12");
        assertTrue(r.emailSent());
        assertEquals("sara@example.com", r.email(), "email is normalised");
        User u = f.user("sara@example.com");
        assertFalse(u.isEmailVerified());
        assertNotEquals("secret12", u.getPassword(), "password is hashed");
        assertTrue(f.studentRepo.findByUserId(u.getId()).isPresent());
        assertTrue(f.code("sara@example.com").matches("\\d{6}"));
        assertEquals(403, status(() -> f.auth.login(new LoginRequest("sara@example.com", "secret12"))), "cannot log in before verifying");
    }

    @Test
    void employerRegistrationNeedsACompanyName() {
        Fixture f = new Fixture();
        assertEquals(400, status(() -> f.auth.register(new RegisterRequest("Eve", "eve@example.com", "secret12", Role.EMPLOYER, "  "), null)));
        assertTrue(f.users.isEmpty(), "a rejected signup creates nothing");
        RegisterResponse r = f.auth.register(new RegisterRequest("Eve", "eve@example.com", "secret12", Role.EMPLOYER, " Acme Ltd "), null);
        assertTrue(r.emailSent());
        assertEquals("Acme Ltd", f.employerRepo.findByUserId(f.user("eve@example.com").getId()).get().getCompanyName());
    }

    @Test
    void adminSignupAndOverlongPasswordsAreRefused() {
        Fixture f = new Fixture();
        assertEquals(400, status(() -> f.auth.register(new RegisterRequest("X", "x@example.com", "secret12", Role.ADMIN, null), null)));
        assertEquals(400, status(() -> f.registerStudent("y@example.com", "ক".repeat(30))));
        assertTrue(f.users.isEmpty());
    }

    @Test
    void duplicateVerifiedEmailIsRefusedAndNothingChanges() {
        Fixture f = new Fixture();
        f.registerStudent("sara@example.com", "secret12");
        f.auth.verifyOtp(new VerifyOtpRequest("sara@example.com", f.code("sara@example.com")));
        String hash = f.user("sara@example.com").getPassword();
        assertEquals(409, status(() -> f.registerStudent("SARA@example.com", "other-pass")));
        assertEquals(hash, f.user("sara@example.com").getPassword());
        assertEquals(1, f.mail.count("otp"));
    }

    @Test
    void secondSignupForAnUnverifiedEmailInsideTheCooldownChangesNothing() {
        Fixture f = new Fixture();
        f.registerStudent("sara@example.com", "owner-pass");
        String hash = f.user("sara@example.com").getPassword();
        // an attacker (or a double click) registers the same address again right away
        assertEquals(429, status(() -> f.registerStudent("sara@example.com", "attacker-pass")));
        assertEquals(hash, f.user("sara@example.com").getPassword(), "the password under the owner's code must not be swapped");
        assertEquals(1, f.mail.count("otp"), "no second email");
        // the owner's code still works and logs in with the OWNER's password
        f.auth.verifyOtp(new VerifyOtpRequest("sara@example.com", f.code("sara@example.com")));
        assertEquals(200, status(() -> f.auth.login(new LoginRequest("sara@example.com", "owner-pass"))));
        assertEquals(401, status(() -> f.auth.login(new LoginRequest("sara@example.com", "attacker-pass"))));
    }

    @Test
    void signupAfterTheCooldownReplacesAnEarlierUnverifiedSignup_noSquatting() {
        Fixture f = new Fixture();
        // an attacker pre-registers the victim's address with a password they know
        f.registerStudent("victim@example.com", "attacker-pass");
        String attackerCode = f.code("victim@example.com");
        f.expireCooldown("victim@example.com");
        // the real owner signs up later with their own password (and as an employer, to prove role/profile are redone)
        f.auth.register(new RegisterRequest("Victim", "victim@example.com", "victim-pass", Role.EMPLOYER, "Victim Co"), null);
        assertEquals(1, f.users.size(), "same account, not a duplicate");
        User u = f.user("victim@example.com");
        assertEquals(Role.EMPLOYER, u.getRole());
        assertEquals("Victim", u.getName());
        assertTrue(f.studentRepo.findByUserId(u.getId()).isEmpty(), "the old student profile is gone");
        assertTrue(f.employerRepo.findByUserId(u.getId()).isPresent());
        // the attacker's earlier code is dead; only the new one verifies, and only the owner's password works
        String newCode = f.code("victim@example.com");
        if (!newCode.equals(attackerCode)) {
            assertEquals(400, status(() -> f.auth.verifyOtp(new VerifyOtpRequest("victim@example.com", attackerCode))));
        }
        f.auth.verifyOtp(new VerifyOtpRequest("victim@example.com", newCode));
        assertEquals(200, status(() -> f.auth.login(new LoginRequest("victim@example.com", "victim-pass"))));
        assertEquals(401, status(() -> f.auth.login(new LoginRequest("victim@example.com", "attacker-pass"))));
    }

    // ---------------------------------------------------------------- email delivery failures

    @Test
    void emailFailureKeepsTheAccountAndLetsTheUserResendImmediately() {
        Fixture f = new Fixture();
        f.mail.failing = true;
        RegisterResponse r = f.registerStudent("sara@example.com", "secret12");
        assertFalse(r.emailSent());
        assertTrue(r.message().contains("could not send"), "the user is told, not promised an email");
        assertTrue(f.userRepo.findByEmailIgnoreCase("sara@example.com").isPresent(), "account survives a transient SMTP failure");
        f.mail.failing = false;
        ResendOtpResponse again = f.auth.resendOtp(new ResendOtpRequest("sara@example.com"));    // no 60s wait
        assertTrue(again.emailSent());
        AuthResponse login = f.auth.verifyOtp(new VerifyOtpRequest("sara@example.com", f.code("sara@example.com")));
        assertNotNull(login.token());
    }

    @Test
    void failedResendIsReportedHonestlyAndStaysRetryable() {
        Fixture f = new Fixture();
        f.registerStudent("sara@example.com", "secret12");
        f.expireCooldown("sara@example.com");
        f.mail.failing = true;
        ResendOtpResponse r = f.auth.resendOtp(new ResendOtpRequest("sara@example.com"));
        assertFalse(r.emailSent());
        f.mail.failing = false;
        assertTrue(f.auth.resendOtp(new ResendOtpRequest("sara@example.com")).emailSent());
    }

    @Test
    void mailSenderCrashNeverTurnsSignupIntoAServerError() {
        Fixture f = new Fixture();
        f.mail.throwing = true;
        RegisterResponse r = f.registerStudent("sara@example.com", "secret12");
        assertFalse(r.emailSent());
        assertTrue(f.userRepo.findByEmailIgnoreCase("sara@example.com").isPresent());
    }

    // ---------------------------------------------------------------- verify / resend / login behaviour

    @Test
    void verifyOtpActivatesTheAccountAndReturnsATokenOnce() {
        Fixture f = new Fixture();
        f.registerStudent("sara@example.com", "secret12");
        String code = f.code("sara@example.com");
        AuthResponse ok = f.auth.verifyOtp(new VerifyOtpRequest("sara@example.com", code));
        assertNotNull(ok.token());
        assertTrue(f.user("sara@example.com").isEmailVerified());
        assertEquals(400, status(() -> f.auth.verifyOtp(new VerifyOtpRequest("sara@example.com", code))), "code reuse");
        assertEquals(200, status(() -> f.auth.login(new LoginRequest("sara@example.com", "secret12"))));
    }

    @Test
    void verifyGivesTheSameAnswerForUnknownVerifiedAndWronglyCodedAccounts() {
        Fixture f = new Fixture();
        f.registerStudent("pending@example.com", "secret12");
        f.registerStudent("done@example.com", "secret12");
        f.auth.verifyOtp(new VerifyOtpRequest("done@example.com", f.code("done@example.com")));
        String wrong = f.code("pending@example.com").equals("000000") ? "111111" : "000000";
        String[] messages = new String[3];
        int i = 0;
        for (String email : new String[]{"nobody@example.com", "done@example.com", "pending@example.com"}) {
            try { f.auth.verifyOtp(new VerifyOtpRequest(email, wrong)); fail("should have failed"); }
            catch (ApiException e) { assertEquals(400, e.getStatus().value()); messages[i++] = e.getMessage(); }
        }
        assertEquals(messages[0], messages[1]);
        assertEquals(messages[1], messages[2]);
    }

    @Test
    void resendGivesTheSameAnswerForUnknownAndVerifiedEmailsAndSendsNothingForThem() {
        Fixture f = new Fixture();
        f.registerStudent("done@example.com", "secret12");
        f.auth.verifyOtp(new VerifyOtpRequest("done@example.com", f.code("done@example.com")));
        long before = f.mail.count("otp");
        ResendOtpResponse unknown = f.auth.resendOtp(new ResendOtpRequest("nobody@example.com"));
        ResendOtpResponse verified = f.auth.resendOtp(new ResendOtpRequest("done@example.com"));
        assertEquals(unknown.message(), verified.message());
        assertEquals(unknown.emailSent(), verified.emailSent());
        assertEquals(before, f.mail.count("otp"));
    }

    @Test
    void resendIsRateLimitedPerAccount() {
        Fixture f = new Fixture();
        f.registerStudent("sara@example.com", "secret12");
        for (int i = 0; i < 5; i++) {
            assertEquals(429, status(() -> f.auth.resendOtp(new ResendOtpRequest("sara@example.com"))));
        }
        assertEquals(1, f.mail.count("otp"), "hammering resend cannot mail-bomb the address");
    }

    @Test
    void loginFailuresLookTheSameForUnknownEmailAndWrongPassword_butUnverifiedIsExplainedOnlyAfterTheRightPassword() {
        Fixture f = new Fixture();
        f.registerStudent("sara@example.com", "secret12");
        ApiException unknown = assertThrows(ApiException.class, () -> f.auth.login(new LoginRequest("nobody@example.com", "secret12")));
        ApiException wrongPw = assertThrows(ApiException.class, () -> f.auth.login(new LoginRequest("sara@example.com", "nope-nope")));
        assertEquals(401, unknown.getStatus().value());
        assertEquals(401, wrongPw.getStatus().value());
        assertEquals(unknown.getMessage(), wrongPw.getMessage());
        ApiException unverified = assertThrows(ApiException.class, () -> f.auth.login(new LoginRequest("sara@example.com", "secret12")));
        assertEquals(403, unverified.getStatus().value());
        assertTrue(unverified.getMessage().toLowerCase().contains("verify your email"), "login.js keys its 'Verify now' link on this text");
    }

    // ---------------------------------------------------------------- registration with an optional CV

    @Test
    void validCvIsStoredAndAttachedToTheNewStudent() throws IOException {
        Fixture f = new Fixture();
        f.auth.register(new RegisterRequest("Sara", "sara@example.com", "secret12", Role.STUDENT, null),
                new FakeUpload("my cv.pdf", "application/pdf", FakeUpload.pdf()));
        StudentProfile p = f.studentRepo.findByUserId(f.user("sara@example.com").getId()).get();
        assertTrue(p.hasCv());
        assertEquals("my cv.pdf", p.getCvOriginalFilename());
        assertEquals(1, f.fileCount());
    }

    @Test
    void invalidCvRejectsTheSignupWithoutLeavingAnAccountOrFile() throws IOException {
        Fixture f = new Fixture();
        int code = status(() -> f.auth.register(new RegisterRequest("Sara", "sara@example.com", "secret12", Role.STUDENT, null),
                new FakeUpload("cv.pdf", "application/pdf", FakeUpload.text("not a pdf"))));
        assertEquals(400, code);
        assertTrue(f.users.isEmpty(), "no half-created account that would block a retry with the same email");
        assertEquals(0, f.fileCount());
        assertEquals(0, f.mail.sent.size());
        // retry with a good file works with the same address
        f.auth.register(new RegisterRequest("Sara", "sara@example.com", "secret12", Role.STUDENT, null),
                new FakeUpload("cv.pdf", "application/pdf", FakeUpload.pdf()));
    }

    @Test
    void employersCannotAttachACv() throws IOException {
        Fixture f = new Fixture();
        assertEquals(400, status(() -> f.auth.register(new RegisterRequest("Eve", "eve@example.com", "secret12", Role.EMPLOYER, "Acme"),
                new FakeUpload("cv.pdf", "application/pdf", FakeUpload.pdf()))));
        assertEquals(0, f.fileCount());
    }

    @Test
    void cvSurvivesAFailedVerificationEmail() throws IOException {
        Fixture f = new Fixture();
        f.mail.failing = true;
        f.auth.register(new RegisterRequest("Sara", "sara@example.com", "secret12", Role.STUDENT, null),
                new FakeUpload("cv.pdf", "application/pdf", FakeUpload.pdf()));
        assertEquals(1, f.fileCount(), "an email problem must not discard the uploaded CV");
    }

    @Test
    void reRegisteringReplacesTheOldUnverifiedCvFile() throws IOException {
        Fixture f = new Fixture();
        f.auth.register(new RegisterRequest("Sara", "sara@example.com", "secret12", Role.STUDENT, null),
                new FakeUpload("old.pdf", "application/pdf", FakeUpload.pdf()));
        f.expireCooldown("sara@example.com");
        f.auth.register(new RegisterRequest("Sara", "sara@example.com", "secret12", Role.STUDENT, null),
                new FakeUpload("new.pdf", "application/pdf", FakeUpload.pdf()));
        assertEquals(1, f.fileCount(), "the superseded file is not left orphaned");
        assertEquals("new.pdf", f.studentRepo.findByUserId(f.user("sara@example.com").getId()).get().getCvOriginalFilename());
    }
}
