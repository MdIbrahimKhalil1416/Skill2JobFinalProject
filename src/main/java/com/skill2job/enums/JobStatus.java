package com.skill2job.enums;

public enum JobStatus {
    /** Created but not yet publicly visible: the employer is past their free job-post quota and
     * has no active Pro subscription, so the posting fee must be paid before this job opens
     * (see PaymentService). Excluded from every public job search. */
    PENDING_PAYMENT,
    OPEN,
    CLOSED
}
