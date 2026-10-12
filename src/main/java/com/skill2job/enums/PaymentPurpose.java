package com.skill2job.enums;

/**
 * What a Payment is for. JOB_POST is a one-time fee to publish a single job past the free quota;
 * SUBSCRIPTION upgrades the employer to the Pro plan (unlimited job posts) for a fixed number of days;
 * STUDENT_PRO upgrades a student to Student Pro for a fixed number of days.
 *
 * Every purpose belongs to exactly one role - {@link #payerRole()} is the single place that says which -
 * so an employer payment can never be started by, or applied to, a student (and vice versa).
 */
public enum PaymentPurpose {
    JOB_POST(Role.EMPLOYER), SUBSCRIPTION(Role.EMPLOYER), STUDENT_PRO(Role.STUDENT);

    private final Role payerRole;

    PaymentPurpose(Role payerRole) {
        this.payerRole = payerRole;
    }

    public Role payerRole() {
        return payerRole;
    }
}
