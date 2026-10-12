package com.skill2job.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationStatusTest {

    @Test
    void validTransitions() {
        assertTrue(ApplicationStatus.APPLIED.canMoveTo(ApplicationStatus.SHORTLISTED));
        assertTrue(ApplicationStatus.APPLIED.canMoveTo(ApplicationStatus.REJECTED));
        assertTrue(ApplicationStatus.SHORTLISTED.canMoveTo(ApplicationStatus.HIRED));
        assertTrue(ApplicationStatus.SHORTLISTED.canMoveTo(ApplicationStatus.REJECTED));
    }

    @Test
    void invalidTransitions() {
        assertFalse(ApplicationStatus.APPLIED.canMoveTo(ApplicationStatus.HIRED));
        assertFalse(ApplicationStatus.HIRED.canMoveTo(ApplicationStatus.REJECTED));
        assertFalse(ApplicationStatus.REJECTED.canMoveTo(ApplicationStatus.SHORTLISTED));
        assertFalse(ApplicationStatus.APPLIED.canMoveTo(null));
    }
}
