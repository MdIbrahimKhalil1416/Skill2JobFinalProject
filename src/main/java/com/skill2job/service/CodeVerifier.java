package com.skill2job.service;

import com.skill2job.entity.CodeDoc;
import com.skill2job.exception.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

/**
 * Shared verification of a one-time code (signup OTP and password-reset code).
 *
 * Every failure - no code, wrong code, expired, already used, too many attempts, unknown account - produces the
 * SAME message, so the endpoint cannot be used to find out which emails have accounts or pending codes. The
 * attempt is reserved atomically before comparing, and success consumes the code atomically.
 */
final class CodeVerifier {

    static final String INVALID = "That code is incorrect or has expired. Request a new code if it keeps failing.";

    private static volatile String dummyHash;

    private CodeVerifier() { }

    /** @param doc the stored code, or null when there is none (the answer and the work done are the same). */
    static void verify(CodeStore store, PasswordEncoder encoder, Class<? extends CodeDoc> type, CodeDoc doc,
                       String rawCode, int maxAttempts, Instant now) {
        if (doc == null) {
            burn(encoder, rawCode);
            throw ApiException.badRequest(INVALID);
        }
        boolean live = !doc.isUsed() && doc.getExpiresAt() != null && !now.isAfter(doc.getExpiresAt())
                && doc.getAttempts() < maxAttempts;
        if (!live || !store.reserveAttempt(type, doc.getId(), maxAttempts, now)) {
            burn(encoder, rawCode);
            throw ApiException.badRequest(INVALID);
        }
        if (rawCode == null || !encoder.matches(rawCode, doc.storedHash())) {
            throw ApiException.badRequest(INVALID);
        }
        if (!store.markUsed(type, doc.getId(), now)) {
            throw ApiException.badRequest(INVALID);      // another request consumed it first
        }
    }

    /** Spends a BCrypt comparison so "no such code" takes about as long as "wrong code". */
    static void burn(PasswordEncoder encoder, String rawCode) {
        String h = dummyHash;
        if (h == null) {
            h = encoder.encode("not-a-real-code");
            dummyHash = h;
        }
        encoder.matches(rawCode == null ? "" : rawCode, h);
    }
}
