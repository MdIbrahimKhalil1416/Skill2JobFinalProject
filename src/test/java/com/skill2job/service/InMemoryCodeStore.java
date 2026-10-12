package com.skill2job.service;

import com.skill2job.entity.CodeDoc;
import com.skill2job.entity.EmailOtp;
import com.skill2job.entity.PasswordResetCode;

import java.time.Instant;

/**
 * Test double for {@link CodeStore}: each step is atomic (synchronized on the document) exactly like the
 * single-document conditional updates of MongoCodeStore, and works directly on the objects the repositories hold.
 * (Proves the service logic given atomic steps; the Mongo queries themselves are not exercised.)
 */
class InMemoryCodeStore implements CodeStore {

    private final java.util.function.Function<String, CodeDoc> lookup;

    InMemoryCodeStore(java.util.function.Function<String, CodeDoc> lookup) {
        this.lookup = lookup;
    }

    @Override
    public boolean reserveAttempt(Class<? extends CodeDoc> type, String id, int maxAttempts, Instant now) {
        CodeDoc d = lookup.apply(id);
        if (d == null) return false;
        synchronized (d) {
            if (d.isUsed() || d.getExpiresAt() == null || !d.getExpiresAt().isAfter(now) || d.getAttempts() >= maxAttempts) {
                return false;
            }
            set(d, d.getAttempts() + 1, d.isUsed());
            return true;
        }
    }

    @Override
    public boolean markUsed(Class<? extends CodeDoc> type, String id, Instant now) {
        CodeDoc d = lookup.apply(id);
        if (d == null) return false;
        synchronized (d) {
            if (d.isUsed() || d.getExpiresAt() == null || !d.getExpiresAt().isAfter(now)) return false;
            set(d, d.getAttempts(), true);
            return true;
        }
    }

    private static void set(CodeDoc d, int attempts, boolean used) {
        if (d instanceof EmailOtp o) { o.setAttempts(attempts); o.setUsed(used); }
        else if (d instanceof PasswordResetCode p) { p.setAttempts(attempts); p.setUsed(used); }
    }
}
