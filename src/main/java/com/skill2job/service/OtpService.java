package com.skill2job.service;

import com.skill2job.entity.EmailOtp;
import com.skill2job.entity.User;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.EmailOtpRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Generates, (re)sends and verifies the 6-digit signup OTP. One active code per user: resending or
 * re-registering overwrites the previous document rather than piling up old ones.
 *
 * The code itself is never stored as plain text - it reuses the app's existing BCrypt
 * PasswordEncoder bean, the same one used for account passwords.
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.otp.expiry-minutes:5}")
    private int expiryMinutes;

    @Value("${app.otp.resend-cooldown-seconds:60}")
    private int resendCooldownSeconds;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    private final CodeStore codeStore;

    public OtpService(EmailOtpRepository otpRepository, PasswordEncoder passwordEncoder, EmailService emailService,
                      CodeStore codeStore) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.codeStore = codeStore;
    }

    public int getExpiryMinutes() {
        return expiryMinutes;
    }

    /**
     * Creates a fresh code (overwriting any previous one) and emails it. Used right after signup.
     * @return true when the email went out (or, in dev, the code was written to the console). When it did not,
     *         the resend cooldown is cleared so the user can ask again straight away instead of waiting.
     */
    public boolean generateAndSend(User user) {
        String otp = generateCode();
        EmailOtp doc = otpRepository.findByUserId(user.getId()).orElseGet(EmailOtp::new);
        Instant now = Instant.now();
        doc.setUserId(user.getId());
        doc.setEmail(user.getEmail());
        doc.setOtpHash(passwordEncoder.encode(otp));
        doc.setExpiresAt(now.plus(Duration.ofMinutes(expiryMinutes)));
        doc.setUsed(false);
        doc.setAttempts(0);
        doc.setLastSentAt(now);
        if (doc.getCreatedAt() == null) {
            doc.setCreatedAt(now);
        }
        doc = otpRepository.save(doc);
        boolean delivered;
        try {
            delivered = emailService.sendOtpEmail(user.getEmail(), user.getName(), otp, expiryMinutes)
                    || emailService.devConsoleFallback();
        } catch (RuntimeException e) {
            delivered = false;
        }
        if (!delivered) {
            doc.setLastSentAt(null);
            otpRepository.save(doc);
        }
        return delivered;
    }

    /** Seconds the user must still wait before another code may be sent; 0 when sending is allowed. */
    public long cooldownRemainingSeconds(User user) {
        EmailOtp existing = otpRepository.findByUserId(user.getId()).orElse(null);
        if (existing == null || existing.getLastSentAt() == null) {
            return 0;
        }
        Instant cooldownEnds = existing.getLastSentAt().plusSeconds(resendCooldownSeconds);
        Instant now = Instant.now();
        return now.isBefore(cooldownEnds) ? Duration.between(now, cooldownEnds).getSeconds() + 1 : 0;
    }

    /** Same as generateAndSend, but refuses to run again before the cooldown elapses. Used by /resend-otp. */
    public boolean resend(User user) {
        assertCooldownOver(user);
        return generateAndSend(user);
    }

    public void assertCooldownOver(User user) {
        long wait = cooldownRemainingSeconds(user);
        if (wait > 0) {
            throw ApiException.tooManyRequests("Please wait " + wait + " more second(s) before requesting a new code.");
        }
    }

    /**
     * Verifies the code for this user. Every failure gives the same message (see CodeVerifier); the attempt is
     * counted atomically and a correct code can be used once.
     */
    public void verify(String userId, String otp) {
        EmailOtp doc = otpRepository.findByUserId(userId).orElse(null);
        CodeVerifier.verify(codeStore, passwordEncoder, EmailOtp.class, doc, otp, maxAttempts, Instant.now());
    }

    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
