package com.skill2job.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentProProfileTest {

    @Test
    void neverBoughtMeansNotPro() {
        assertFalse(new StudentProfile().isProActive());
    }

    @Test
    void proIsActiveOnlyBeforeItsExpiry() {
        StudentProfile p = new StudentProfile();
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        p.setProExpiresAt(now.plusSeconds(60));
        assertTrue(p.isProActiveAt(now));
        assertFalse(p.isProActiveAt(now.plusSeconds(60)), "expiry instant itself is already expired");
        assertFalse(p.isProActiveAt(now.plusSeconds(3600)));
    }
}
