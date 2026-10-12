package com.skill2job.service;

import com.skill2job.exception.ApiException;

import java.nio.charset.StandardCharsets;

/** BCrypt only uses the first 72 BYTES of a password; longer input is rejected up front instead of failing later. */
public final class PasswordRules {

    public static final int MAX_BYTES = 72;

    private PasswordRules() { }

    public static void check(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Password is required.");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw ApiException.badRequest("Password is too long (at most " + MAX_BYTES
                    + " bytes; characters outside basic English count as more than one).");
        }
    }
}
