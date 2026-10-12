package com.skill2job.service;

import com.skill2job.entity.CodeDoc;

import java.time.Instant;

/**
 * The two atomic steps that make one-time codes safe under parallel requests. Both are single-document
 * conditional updates (no transactions needed), so concurrent guesses can never exceed the attempt limit
 * and a correct code can be consumed by exactly one request.
 */
public interface CodeStore {

    /** Counts one guess against the code, but only if it is still unused, unexpired and under the limit.
     * The attempt is reserved BEFORE the code is compared. @return false when the guess may not be made. */
    boolean reserveAttempt(Class<? extends CodeDoc> type, String id, int maxAttempts, Instant now);

    /** Marks the code used, only if it is still unused and unexpired. @return true for exactly one caller. */
    boolean markUsed(Class<? extends CodeDoc> type, String id, Instant now);
}
