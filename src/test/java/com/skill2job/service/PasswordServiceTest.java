package com.skill2job.service;

import com.skill2job.audit.AuditService;
import com.skill2job.dto.PasswordDtos;
import com.skill2job.entity.CodeDoc;
import com.skill2job.entity.PasswordResetCode;
import com.skill2job.entity.User;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.PasswordResetCodeRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.security.AuthUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Forgot / reset password with a mocked mail sender: expiry, reuse, attempts, enumeration, failure handling. */
class PasswordServiceTest {

    static class QuietAudit extends AuditService {
        QuietAudit() { super(null, null); }
        @Override public void record(String a, String b, String c, String d, boolean e, String f) { }
        @Override public void record(AuthUser u, String a, String b, String c, String d, boolean e, String f) { }
    }

    static final class Fixture {
        final Map<String, Object> users = new LinkedHashMap<>();
        final Map<String, Object> codes = new LinkedHashMap<>();
        final UserRepository userRepo = MapRepo.of(UserRepository.class, users);
        final PasswordResetCodeRepository codeRepo = MapRepo.of(PasswordResetCodeRepository.class, codes);
        final RecordingEmailService mail = new RecordingEmailService();
        final PasswordService svc;
        final User alice;

        Fixture() {
            CodeStore store = new InMemoryCodeStore(id -> (CodeDoc) codes.get(id));
            svc = new PasswordService(userRepo, codeRepo, OtpServiceTest.encoder(), mail, null, new QuietAudit(), null, store);
            OtpServiceTest.set(svc, "expiryMinutes", 15);
            OtpServiceTest.set(svc, "cooldownSeconds", 60);
            OtpServiceTest.set(svc, "maxAttempts", 5);
            alice = new User();
            alice.setEmail("alice@example.com");
            alice.setName("Alice");
            alice.setActive(true);
            alice.setEmailVerified(true);
            alice.setPassword(OtpServiceTest.encoder().encode("oldpass1"));
            userRepo.save(alice);
        }

        void forgot(String email) { svc.forgot(new PasswordDtos.ForgotPasswordRequest(email)); }
        PasswordDtos.MessageResponse reset(String email, String code, String pw) {
            return svc.reset(new PasswordDtos.ResetPasswordRequest(email, code, pw));
        }
        PasswordResetCode doc() { return codeRepo.findByUserId(alice.getId()).get(); }
        String code() { return mail.lastCode("alice@example.com", "reset"); }
    }

    static void assertInvalid(Runnable r) {
        ApiException e = assertThrows(ApiException.class, r::run);
        assertEquals(400, e.getStatus().value());
        assertEquals(CodeVerifier.INVALID, e.getMessage());
    }

    @Test
    void forgotGivesTheSameAnswerForKnownUnknownAndUnverifiedEmails() {
        Fixture f = new Fixture();
        User pending = new User();
        pending.setEmail("pending@example.com");
        pending.setEmailVerified(false);
        pending.setActive(true);
        f.userRepo.save(pending);
        assertEquals(PasswordService.GENERIC_SENT, f.svc.forgot(new PasswordDtos.ForgotPasswordRequest("alice@example.com")).message());
        assertEquals(PasswordService.GENERIC_SENT, f.svc.forgot(new PasswordDtos.ForgotPasswordRequest("nobody@example.com")).message());
        assertEquals(PasswordService.GENERIC_SENT, f.svc.forgot(new PasswordDtos.ForgotPasswordRequest("pending@example.com")).message());
        assertEquals(1, f.mail.count("reset"), "only the real, verified account gets an email");
    }

    @Test
    void repeatedForgotRequestsDuringTheCooldownSendOnlyOneEmail() {
        Fixture f = new Fixture();
        for (int i = 0; i < 10; i++) f.forgot("alice@example.com");
        assertEquals(1, f.mail.count("reset"), "email-bombing a victim is limited to one mail per cooldown");
    }

    @Test
    void resetWithTheEmailedCodeChangesThePasswordAndSignsOutOldTokens() {
        Fixture f = new Fixture();
        f.forgot("alice@example.com");
        Instant before = Instant.now();
        f.reset("alice@example.com", f.code(), "newpass99");
        User u = f.userRepo.findById(f.alice.getId()).get();
        assertEquals(OtpServiceTest.encoder().encode("newpass99"), u.getPassword());
        assertNotNull(u.getPasswordChangedAt(), "stamps the time every older JWT is rejected against");
        assertFalse(u.getPasswordChangedAt().isBefore(before.minusSeconds(1)));
        assertEquals(1, f.mail.count("changed"), "security notice is mailed");
    }

    @Test
    void aUsedResetCodeCannotBeReplayed() {
        Fixture f = new Fixture();
        f.forgot("alice@example.com");
        String code = f.code();
        f.reset("alice@example.com", code, "newpass99");
        assertInvalid(() -> f.reset("alice@example.com", code, "attacker-pass"));
        assertEquals(OtpServiceTest.encoder().encode("newpass99"), f.userRepo.findById(f.alice.getId()).get().getPassword());
    }

    @Test
    void expiredWrongMalformedAndUnknownAccountAllLookIdentical() {
        Fixture f = new Fixture();
        f.forgot("alice@example.com");
        String code = f.code();
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertInvalid(() -> f.reset("alice@example.com", wrong, "newpass99"));
        assertInvalid(() -> f.reset("alice@example.com", "12", "newpass99"));
        assertInvalid(() -> f.reset("nobody@example.com", wrong, "newpass99"));
        f.doc().setExpiresAt(Instant.now().minusSeconds(1));
        assertInvalid(() -> f.reset("alice@example.com", code, "newpass99"));
    }

    @Test
    void tooManyWrongCodesLockTheCodeEvenForTheRightGuess() {
        Fixture f = new Fixture();
        f.forgot("alice@example.com");
        String code = f.code();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertInvalid(() -> f.reset("alice@example.com", wrong, "newpass99"));
        assertInvalid(() -> f.reset("alice@example.com", code, "newpass99"));
        assertEquals(OtpServiceTest.encoder().encode("oldpass1"), f.userRepo.findById(f.alice.getId()).get().getPassword());
    }

    @Test
    void parallelResetsWithTheSameCodeSucceedExactlyOnce() throws Exception {
        Fixture f = new Fixture();
        f.forgot("alice@example.com");
        String code = f.code();
        AtomicInteger ok = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        Thread[] ts = new Thread[30];
        for (int i = 0; i < ts.length; i++) {
            final int n = i;
            ts[i] = new Thread(() -> {
                try { go.await(); f.reset("alice@example.com", code, "pass-number-" + n); ok.incrementAndGet(); } catch (Exception ignored) { }
            });
            ts[i].start();
        }
        go.countDown();
        for (Thread t : ts) t.join();
        assertEquals(1, ok.get());
    }

    @Test
    void failedEmailDoesNotBlockAnImmediateRetry() {
        Fixture f = new Fixture();
        f.mail.failing = true;
        f.forgot("alice@example.com");
        assertEquals(0, f.mail.count("reset"));
        f.mail.failing = false;
        f.forgot("alice@example.com");
        assertEquals(1, f.mail.count("reset"), "no 60s lock-out after a send that never left");
        f.reset("alice@example.com", f.code(), "newpass99");
    }

    @Test
    void mailSenderCrashDuringForgotIsNotAnError() {
        Fixture f = new Fixture();
        f.mail.throwing = true;
        assertEquals(PasswordService.GENERIC_SENT, f.svc.forgot(new PasswordDtos.ForgotPasswordRequest("alice@example.com")).message());
    }

    @Test
    void passwordLongerThan72BytesIsRejectedBeforeAnythingIsConsumed() {
        Fixture f = new Fixture();
        f.forgot("alice@example.com");
        String code = f.code();
        String tooLong = "ক".repeat(30);                    // 30 chars but 90 bytes in UTF-8
        ApiException e = assertThrows(ApiException.class, () -> f.reset("alice@example.com", code, tooLong));
        assertEquals(400, e.getStatus().value());
        assertFalse(f.doc().isUsed(), "a bad password must not burn the code");
        f.reset("alice@example.com", code, "fine-password");
    }
}
