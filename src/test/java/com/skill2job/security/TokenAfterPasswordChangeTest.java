package com.skill2job.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAfterPasswordChangeTest {

    private static final Instant CHANGED = Instant.parse("2026-10-08T10:00:00.750Z");

    @Test
    void noPasswordChangeAcceptsAnyToken() {
        assertTrue(JwtAuthenticationFilter.issuedAfterPasswordChange(Instant.parse("2020-01-01T00:00:00Z"), null));
    }

    @Test
    void tokenIssuedBeforeChangeIsRejected() {
        assertFalse(JwtAuthenticationFilter.issuedAfterPasswordChange(Instant.parse("2026-10-08T09:59:59Z"), CHANGED));
    }

    @Test
    void tokenIssuedInTheSameSecondIsAcceptedBecauseIatHasSecondPrecision() {
        assertTrue(JwtAuthenticationFilter.issuedAfterPasswordChange(Instant.parse("2026-10-08T10:00:00Z"), CHANGED));
    }

    @Test
    void tokenWithoutIssuedAtIsRejectedOnceAPasswordChangeExists() {
        assertFalse(JwtAuthenticationFilter.issuedAfterPasswordChange(null, CHANGED));
    }
}
