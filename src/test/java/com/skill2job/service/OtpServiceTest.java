package com.skill2job.service;

import com.skill2job.entity.CodeDoc;
import com.skill2job.entity.EmailOtp;
import com.skill2job.entity.User;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.EmailOtpRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Signup OTP: hashing, expiry, reuse, attempts (also under parallel guessing), cooldown, email failure. */
class OtpServiceTest {

    static PasswordEncoder encoder() {
        // Deliberately simple and "hash"-looking: the tests check the stored value is not the plain code.
        return new PasswordEncoder() {
            @Override public String encode(CharSequence raw) { return "h$" + new StringBuilder(raw).reverse(); }
            @Override public boolean matches(CharSequence raw, String enc) { return enc != null && enc.equals(encode(raw)); }
        };
    }

    static final class Fixture {
        final Map<String, Object> docs = new LinkedHashMap<>();
        final EmailOtpRepository repo = MapRepo.of(EmailOtpRepository.class, docs);
        final RecordingEmailService mail = new RecordingEmailService();
        final OtpService otp;
        /** How many guesses were really compared against the stored code (not the dummy burn-time comparison). */
        final java.util.concurrent.atomic.AtomicInteger comparisons = new java.util.concurrent.atomic.AtomicInteger();

        Fixture() {
            CodeStore store = new InMemoryCodeStore(id -> (CodeDoc) docs.get(id));
            PasswordEncoder base = encoder();
            PasswordEncoder counting = new PasswordEncoder() {
                @Override public String encode(CharSequence raw) { return base.encode(raw); }
                @Override public boolean matches(CharSequence raw, String enc) {
                    if (enc != null && docs.values().stream().anyMatch(d -> enc.equals(((EmailOtp) d).getOtpHash()))) comparisons.incrementAndGet();
                    return base.matches(raw, enc);
                }
            };
            otp = new OtpService(repo, counting, mail, store);
            set(otp, "expiryMinutes", 5);
            set(otp, "resendCooldownSeconds", 60);
            set(otp, "maxAttempts", 5);
        }

        EmailOtp doc(User u) { return repo.findByUserId(u.getId()).get(); }
    }

    static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static User user(String id) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@example.com");
        u.setName("User " + id);
        return u;
    }

    static void assertInvalid(Runnable r) {
        ApiException e = assertThrows(ApiException.class, r::run);
        assertEquals(400, e.getStatus().value());
        assertEquals(CodeVerifier.INVALID, e.getMessage());
    }

    @Test
    void codeIsEmailedAndOnlyItsHashIsStored() {
        Fixture f = new Fixture();
        User u = user("u1");
        assertTrue(f.otp.generateAndSend(u));
        String code = f.mail.lastCode(u.getEmail(), "otp");
        assertTrue(code.matches("\\d{6}"));
        EmailOtp d = f.doc(u);
        assertNotNull(d.getOtpHash());
        assertFalse(d.getOtpHash().contains(code) && d.getOtpHash().equals(code), "plain code must not be stored");
        assertFalse(d.getOtpHash().equals(code));
    }

    @Test
    void correctCodeVerifiesOnceAndCannotBeReused() {
        Fixture f = new Fixture();
        User u = user("u1");
        f.otp.generateAndSend(u);
        String code = f.mail.lastCode(u.getEmail(), "otp");
        f.otp.verify(u.getId(), code);
        assertTrue(f.doc(u).isUsed());
        assertInvalid(() -> f.otp.verify(u.getId(), code));
    }

    @Test
    void wrongExpiredAndMalformedCodesAreAllRejectedWithTheSameMessage() {
        Fixture f = new Fixture();
        User u = user("u1");
        f.otp.generateAndSend(u);
        String code = f.mail.lastCode(u.getEmail(), "otp");
        String wrong = code.equals("000000") ? "111111" : "000000";
        assertInvalid(() -> f.otp.verify(u.getId(), wrong));
        assertInvalid(() -> f.otp.verify(u.getId(), null));
        assertInvalid(() -> f.otp.verify(u.getId(), ""));
        assertInvalid(() -> f.otp.verify(u.getId(), "12345"));
        assertInvalid(() -> f.otp.verify(u.getId(), "abcdef"));
        assertInvalid(() -> f.otp.verify(u.getId(), code + " "));
        f.doc(u).setExpiresAt(Instant.now().minusSeconds(1));
        assertInvalid(() -> f.otp.verify(u.getId(), code));       // right code, but expired: server-side expiry
        assertInvalid(() -> f.otp.verify("nobody", code));        // no code at all: indistinguishable
    }

    @Test
    void afterMaxAttemptsEvenTheCorrectCodeIsRefusedUntilANewOneIsSent() {
        Fixture f = new Fixture();
        User u = user("u1");
        f.otp.generateAndSend(u);
        String code = f.mail.lastCode(u.getEmail(), "otp");
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertInvalid(() -> f.otp.verify(u.getId(), wrong));
        assertEquals(5, f.doc(u).getAttempts());
        assertInvalid(() -> f.otp.verify(u.getId(), code));
        assertFalse(f.doc(u).isUsed());
        // a fresh code (after the cooldown) works again and resets the counter
        f.doc(u).setLastSentAt(Instant.now().minusSeconds(120));
        assertTrue(f.otp.resend(u));
        f.otp.verify(u.getId(), f.mail.lastCode(u.getEmail(), "otp"));
    }

    @Test
    void parallelWrongGuessesCanNeverExceedTheAttemptLimit() throws Exception {
        Fixture f = new Fixture();
        User u = user("u1");
        f.otp.generateAndSend(u);
        String code = f.mail.lastCode(u.getEmail(), "otp");
        String wrong = code.equals("000000") ? "111111" : "000000";
        int threads = 60;
        CountDownLatch go = new CountDownLatch(1);
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try { go.await(); f.otp.verify(u.getId(), wrong); } catch (Exception ignored) { }
            });
            ts[i].start();
        }
        go.countDown();
        for (Thread t : ts) t.join();
        assertEquals(5, f.doc(u).getAttempts(), "exactly the allowed number of guesses may be spent");
        assertEquals(5, f.comparisons.get(), "no more than 5 guesses may ever be checked, however many arrive in parallel");
    }

    @Test
    void aCorrectCodeSubmittedInParallelSucceedsExactlyOnce() throws Exception {
        Fixture f = new Fixture();
        User u = user("u1");
        f.otp.generateAndSend(u);
        String code = f.mail.lastCode(u.getEmail(), "otp");
        AtomicInteger ok = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        Thread[] ts = new Thread[40];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                try { go.await(); f.otp.verify(u.getId(), code); ok.incrementAndGet(); } catch (Exception ignored) { }
            });
            ts[i].start();
        }
        go.countDown();
        for (Thread t : ts) t.join();
        assertEquals(1, ok.get());
    }

    @Test
    void resendIsBlockedDuringTheCooldownAndAllowedAfter() {
        Fixture f = new Fixture();
        User u = user("u1");
        f.otp.generateAndSend(u);
        ApiException e = assertThrows(ApiException.class, () -> f.otp.resend(u));
        assertEquals(429, e.getStatus().value());
        assertEquals(1, f.mail.count("otp"), "no second email during the cooldown");
        f.doc(u).setLastSentAt(Instant.now().minusSeconds(61));
        assertTrue(f.otp.resend(u));
        assertEquals(2, f.mail.count("otp"));
    }

    @Test
    void resendReplacesTheOldCode() {
        Fixture f = new Fixture();
        User u = user("u1");
        f.otp.generateAndSend(u);
        String first = f.mail.lastCode(u.getEmail(), "otp");
        f.doc(u).setLastSentAt(Instant.now().minusSeconds(120));
        f.otp.resend(u);
        String second = f.mail.lastCode(u.getEmail(), "otp");
        if (!first.equals(second)) {
            assertInvalid(() -> f.otp.verify(u.getId(), first));
        }
        f.otp.verify(u.getId(), second);
    }

    @Test
    void failedEmailDoesNotStartTheCooldownSoTheUserCanRetryAtOnce() {
        Fixture f = new Fixture();
        User u = user("u1");
        f.mail.failing = true;
        assertFalse(f.otp.generateAndSend(u));
        assertNull(f.doc(u).getLastSentAt());
        assertEquals(0, f.otp.cooldownRemainingSeconds(u));
        f.mail.failing = false;
        assertTrue(f.otp.resend(u), "retry works immediately, no 60s wait for an email that never left");
        f.otp.verify(u.getId(), f.mail.lastCode(u.getEmail(), "otp"));
    }

    @Test
    void anExplodingMailSenderIsTreatedAsAFailedSendNotAnError() {
        Fixture f = new Fixture();
        User u = user("u1");
        f.mail.throwing = true;
        assertFalse(f.otp.generateAndSend(u));
        assertNotNull(f.doc(u), "the code document still exists, so a retry can replace it");
    }

    @Test
    void devConsoleFallbackCountsAsDelivered() {
        Fixture f = new Fixture();
        f.mail.failing = true;
        f.mail.devFallback = true;
        assertTrue(f.otp.generateAndSend(user("u1")));
    }
}
