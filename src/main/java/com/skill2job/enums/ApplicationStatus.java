package com.skill2job.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Application workflow:
 * APPLIED -> SHORTLISTED or REJECTED
 * SHORTLISTED -> HIRED or REJECTED
 * HIRED and REJECTED are final.
 */
public enum ApplicationStatus {
    APPLIED, SHORTLISTED, REJECTED, HIRED;

    public Set<ApplicationStatus> allowedNext() {
        return switch (this) {
            case APPLIED -> EnumSet.of(SHORTLISTED, REJECTED);
            case SHORTLISTED -> EnumSet.of(HIRED, REJECTED);
            case REJECTED, HIRED -> EnumSet.noneOf(ApplicationStatus.class);
        };
    }

    public boolean canMoveTo(ApplicationStatus next) {
        return next != null && allowedNext().contains(next);
    }
}
