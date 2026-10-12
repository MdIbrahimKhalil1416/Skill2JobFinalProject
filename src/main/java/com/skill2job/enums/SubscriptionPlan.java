package com.skill2job.enums;

/** FREE: a limited number of job posts (app.payment.free-job-posts), then each extra job needs a
 * one-time payment. PRO: unlimited job posts while app.payment.subscription-days runs; see
 * EmployerProfile.isProActive(). */
public enum SubscriptionPlan {
    FREE, PRO
}
