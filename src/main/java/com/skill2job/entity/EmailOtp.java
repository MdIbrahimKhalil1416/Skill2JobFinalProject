package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One active signup-verification OTP per user. Kept in its own collection (not on User) because it
 * has its own lifecycle (expires, gets reused on resend, is deleted after success) that has nothing
 * to do with the rest of the account. Only a BCrypt hash of the code is stored, never the plain OTP.
 */
@Document(collection = "email_otps")
public class EmailOtp implements CodeDoc {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String email;
    /** BCrypt hash of the 6-digit code (reuses the app's PasswordEncoder bean). */
    private String otpHash;
    private Instant expiresAt;
    private boolean used;
    /** Failed verification attempts against the current code; too many forces a resend. */
    private int attempts;
    /** When the current code was (re)sent; used to enforce the resend cooldown. */
    private Instant lastSentAt;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isUsed() { return used; }
    public void setUsed(boolean used) { this.used = used; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override public String storedHash() { return otpHash; }
}
