package com.skill2job.entity;

import java.time.Instant;

/** What the one-time-code documents (signup OTP, password-reset code) have in common. */
public interface CodeDoc {
    String getId();
    Instant getExpiresAt();
    boolean isUsed();
    int getAttempts();
    /** The BCrypt hash of the code - never the code itself. */
    String storedHash();
}
