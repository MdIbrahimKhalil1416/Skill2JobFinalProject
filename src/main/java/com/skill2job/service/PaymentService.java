package com.skill2job.service;

import com.skill2job.audit.AuditService;
import com.skill2job.dto.PaymentInitResponse;
import com.skill2job.dto.PaymentResponse;
import com.skill2job.dto.StudentPremiumResponse;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Job;
import com.skill2job.entity.Payment;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;
import com.skill2job.enums.SubscriptionPlan;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.PaymentRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payment module (employer job-posting fee + Pro subscription), backed by SSLCommerz sandbox.
 *
 * Flow for a job-posting fee:
 *  1. JobService.create() decides whether the new job needs payment (jobPostingRequiresPayment) and,
 *     if so, saves it as JobStatus.PENDING_PAYMENT instead of OPEN - it never shows up in a public
 *     search either way, so there is no window where an unpaid job is visible.
 *  2. The frontend calls initiateJobPayment(jobId), which opens an SSLCommerz checkout session and
 *     returns a GatewayPageURL for a full-page redirect.
 *  3. SSLCommerz redirects the browser back to one of the /api/payments/sslcommerz/* endpoints below
 *     (public - see SecurityConfig), which call handleSuccess/handleFail/handleCancel. A SUCCESS is
 *     re-confirmed server-to-server via SslCommerzClient.validate() before anything is applied, and
 *     applying it (opening the job / extending the subscription) is idempotent, since SSLCommerz may
 *     also call the ipn endpoint for the same transaction.
 *
 * Flow for a Pro subscription is the same, minus the job: initiateSubscriptionPayment() instead,
 * and a successful payment extends EmployerProfile.subscriptionExpiresAt instead of opening a job.
 *
 * Student Pro (initiateStudentProPayment) uses the very same session/callback/validation code; only the
 * owner (Payment.studentId) and the effect (StudentProfile.proExpiresAt) differ. Which role may start and
 * receive which purpose is decided in one place - PaymentPurpose.payerRole() / PaymentRules.inconsistency().
 *
 * Duplicate and concurrent callbacks: the browser redirect and the IPN can arrive at the same moment, so
 * "check status, then save" is not enough. The PENDING/FAILED/CANCELLED -> SUCCESS transition is a single
 * atomic conditional update (PaymentStore.claimSuccess), and the effect (open the job / extend the plan) is
 * idempotent per payment, so running it twice or concurrently cannot double it. A confirmed payment whose
 * activation failed keeps effectPending=true and is retried by later callbacks and by recover().
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final JobRepository jobRepository;
    private final NotificationService notificationService;
    private final SslCommerzClient sslCommerzClient;
    private final AuditService auditService;
    private final PaymentStore store;
    private final StudentProfileRepository studentProfileRepository;

    @Value("${app.payment.job-post-fee}")
    private double jobPostFee;
    @Value("${app.payment.free-job-posts}")
    private int freeJobPosts;
    @Value("${app.payment.subscription-price}")
    private double subscriptionPrice;
    @Value("${app.payment.subscription-days}")
    private int subscriptionDays;
    @Value("${app.payment.student-pro-price:299}")
    private double studentProPrice;
    @Value("${app.payment.student-pro-days:30}")
    private int studentProDays;
    @Value("${app.payment.base-url}")
    private String baseUrl;

    public PaymentService(PaymentRepository paymentRepository, JobRepository jobRepository,
                          NotificationService notificationService,
                          SslCommerzClient sslCommerzClient, AuditService auditService,
                          PaymentStore store, StudentProfileRepository studentProfileRepository) {
        this.paymentRepository = paymentRepository;
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
        this.sslCommerzClient = sslCommerzClient;
        this.auditService = auditService;
        this.store = store;
        this.studentProfileRepository = studentProfileRepository;
    }

    /** True once the employer has used up their free job posts and has no active Pro subscription.
     * Reads EmployerProfile.jobsPostedCount - a persistent, never-decremented counter - rather than a
     * live count of the employer's current jobs, so deleting a job can never refund a used free slot. */
    public boolean jobPostingRequiresPayment(EmployerProfile profile) {
        if (profile.isProActive()) {
            return false;
        }
        return profile.getJobsPostedCount() >= freeJobPosts;
    }

    public double jobPostFee() { return jobPostFee; }
    public int freeJobPosts() { return freeJobPosts; }
    public double subscriptionPrice() { return subscriptionPrice; }
    public int subscriptionDays() { return subscriptionDays; }
    public double studentProPrice() { return studentProPrice; }
    public int studentProDays() { return studentProDays; }

    public PaymentInitResponse initiateJobPayment(AuthUser employer, String jobId) {
        requireRole(employer, Role.EMPLOYER);
        Job job = jobRepository.findById(jobId).orElseThrow(() -> ApiException.notFound("Job not found."));
        if (!job.getEmployerId().equals(employer.id())) {
            throw ApiException.forbidden("You can only pay to publish your own job.");
        }
        if (job.getStatus() != JobStatus.PENDING_PAYMENT) {
            throw ApiException.badRequest("This job does not need payment.");
        }

        Payment payment = new Payment();
        payment.setEmployerId(employer.id());
        payment.setPayerRoleValue(Role.EMPLOYER);
        payment.setTranId(newTranId("JOB", job.getId()));
        payment.setPurpose(PaymentPurpose.JOB_POST);
        payment.setJobId(job.getId());
        payment.setAmount(jobPostFee);
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);

        return openSession(payment, employer, "Job posting fee: " + job.getTitle());
    }

    public PaymentInitResponse initiateSubscriptionPayment(AuthUser employer, SubscriptionPlan plan) {
        requireRole(employer, Role.EMPLOYER);
        if (plan != SubscriptionPlan.PRO) {
            throw ApiException.badRequest("There is nothing to pay for the Free plan.");
        }
        Payment payment = new Payment();
        payment.setEmployerId(employer.id());
        payment.setPayerRoleValue(Role.EMPLOYER);
        payment.setTranId(newTranId("SUB", employer.id()));
        payment.setPurpose(PaymentPurpose.SUBSCRIPTION);
        payment.setPlan(plan);
        payment.setAmount(subscriptionPrice);
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);

        return openSession(payment, employer, "Skill2Job Pro subscription (" + subscriptionDays + " days)");
    }

    /** Starts a Student Pro purchase (or renewal). The payment is bound to the logged-in student - the
     * client sends no student id, so one student can never pay on behalf of, or be credited to, another. */
    public PaymentInitResponse initiateStudentProPayment(AuthUser student) {
        requireRole(student, Role.STUDENT);
        Payment payment = new Payment();
        payment.setStudentId(student.id());
        payment.setPayerRoleValue(Role.STUDENT);
        payment.setTranId(newTranId("STU", student.id()));
        payment.setPurpose(PaymentPurpose.STUDENT_PRO);
        payment.setAmount(studentProPrice);
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);

        return openSession(payment, student, "Skill2Job Student Pro (" + studentProDays + " days)");
    }

    /** What the checkout page shows: price, duration, and whether Pro is currently active. */
    public StudentPremiumResponse studentPremium(AuthUser student) {
        requireRole(student, Role.STUDENT);
        Instant expires = studentProfileRepository.findByUserId(student.id())
                .map(StudentProfile::getProExpiresAt).orElse(null);
        boolean active = expires != null && expires.isAfter(Instant.now());
        return new StudentPremiumResponse(active, active ? expires : null, studentProPrice, "BDT", studentProDays);
    }

    private static void requireRole(AuthUser user, Role role) {
        if (user == null || user.role() != role) {
            throw ApiException.forbidden("This payment is only available to " + role.name().toLowerCase() + " accounts.");
        }
    }

    private String newTranId(String prefix, String ownerId) {
        String safeOwner = ownerId.length() > 10 ? ownerId.substring(0, 10) : ownerId;
        return prefix + "-" + safeOwner + "-" + Instant.now().toEpochMilli();
    }

    private PaymentInitResponse openSession(Payment payment, AuthUser payer, String productName) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("total_amount", String.valueOf(payment.getAmount()));
        fields.put("currency", payment.getCurrency());
        fields.put("tran_id", payment.getTranId());
        fields.put("success_url", baseUrl + "/api/payments/sslcommerz/success");
        fields.put("fail_url", baseUrl + "/api/payments/sslcommerz/fail");
        fields.put("cancel_url", baseUrl + "/api/payments/sslcommerz/cancel");
        fields.put("ipn_url", baseUrl + "/api/payments/sslcommerz/ipn");
        fields.put("cus_name", blankToPlaceholder(payer.name(), payer.role() == Role.STUDENT ? "Skill2Job Student" : "Skill2Job Employer"));
        fields.put("cus_email", payer.email());
        fields.put("cus_add1", "Dhaka");
        fields.put("cus_city", "Dhaka");
        fields.put("cus_postcode", "1000");
        fields.put("cus_country", "Bangladesh");
        fields.put("cus_phone", "01700000000");
        fields.put("product_name", productName);
        fields.put("product_category", "Service");
        fields.put("product_profile", "general");
        fields.put("shipping_method", "NO");
        fields.put("num_of_item", "1");

        SslCommerzClient.SessionResult result = sslCommerzClient.createSession(fields);
        if (!result.success()) {
            throw ApiException.serverError("Could not start the payment: " + result.failReason());
        }
        return new PaymentInitResponse(result.gatewayUrl(), payment.getTranId(), payment.getAmount(), payment.getCurrency());
    }

    private static String blankToPlaceholder(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }

    // ---------------------------------------------------------------- SSLCommerz callbacks
    //
    // Rules this section keeps (see PaymentServiceTest):
    //  - Only a val_id that SSLCommerz's Validation API confirms, for THIS payment's tran_id, amount and currency,
    //    can make a payment SUCCESS. The browser redirect is never proof of anything.
    //  - PENDING/FAILED/CANCELLED -> SUCCESS is one atomic conditional update (store.claimSuccess).
    //  - The paid-for effect is idempotent per payment (store.publishJob / extend*Pro), so running it twice,
    //    concurrently or after a crash can never double it. effectPending stays true until it is recorded done,
    //    so a half-finished activation is always visible and is retried by callbacks and by recover().
    //  - A gateway that cannot be reached is NOT a failed payment: the payment stays open and is re-validated later.

    public String handleSuccess(Map<String, String> params) { return processCallback(params, true, PaymentStatus.FAILED); }
    public String handleFail(Map<String, String> params) { return processCallback(params, false, PaymentStatus.FAILED); }
    public String handleCancel(Map<String, String> params) { return processCallback(params, false, PaymentStatus.CANCELLED); }

    /** Server-to-server notification SSLCommerz also sends regardless of whether the customer's
     * browser made it back to success_url - the reliability backup for the redirect flow above. */
    public void handleIpn(Map<String, String> params) {
        processCallback(params, true, PaymentStatus.FAILED);
    }

    private enum Verdict { CONFIRMED, REJECTED, UNAVAILABLE }

    /** @param claimedSuccess true for the success redirect and the IPN (they must prove it with a val_id);
     *     false for fail/cancel.
     *  @param ifNotSuccess the status to record for a fail/cancel callback - FAILED, or CANCELLED for the cancel
     *     endpoint. Which endpoint SSLCommerz called is itself the reliable signal; its callback body is not. */
    private String processCallback(Map<String, String> params, boolean claimedSuccess, PaymentStatus ifNotSuccess) {
        String tranId = params.get("tran_id");
        if (tranId == null || tranId.isBlank()) {
            return baseUrl + "/payment-result.html?status=error";
        }
        Payment payment = store.findByTranId(tranId).orElse(null);
        if (payment == null) {
            log.warn("SSLCommerz callback for an unknown tran_id: {}", tranId);
            return baseUrl + "/payment-result.html?status=error";
        }

        // Already confirmed (the browser redirect and the IPN can both arrive): harmless. If the first callback
        // died before the effect was recorded, this one finishes it - the effect itself is idempotent.
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            finishEffect(tranId);
            return resultRedirect(reload(payment));
        }

        if (claimedSuccess) {
            String valId = params.get("val_id");
            if (valId == null || valId.isBlank()) {
                // A "success" without a val_id proves nothing, and must not be able to change the payment either.
                log.warn("Success callback without val_id for {}; ignored", tranId);
                return resultRedirect(payment);
            }
            switch (verify(payment, valId, params)) {
                case CONFIRMED -> {
                    finishEffect(tranId);
                    return resultRedirect(reload(payment));
                }
                case UNAVAILABLE -> {
                    // The gateway could not be asked. Do NOT fail the payment: keep the val_id and re-validate later.
                    store.recordPendingVerification(payment.getId(), valId);
                    auditService.record(null, "PAYMENT_VERIFY_DEFERRED", "PAYMENT", payment.getTranId(),
                            payment.getPurpose() + " " + payment.getAmount() + " " + payment.getCurrency(), false,
                            "Gateway unavailable; will re-validate");
                    return resultRedirect(reload(payment));
                }
                case REJECTED -> {
                    // Forged, replayed or mismatching data. The legitimate payment is left exactly as it was.
                    return baseUrl + "/payment-result.html?status=error&purpose="
                            + payment.getPurpose().name().toLowerCase() + "&tran_id=" + payment.getTranId();
                }
            }
        }

        if (store.markFailedOrCancelled(payment.getId(), ifNotSuccess)) {
            auditService.record(null, "PAYMENT_" + ifNotSuccess.name(), "PAYMENT", payment.getTranId(),
                    payment.getPurpose() + " " + payment.getAmount() + " " + payment.getCurrency(), false, null);
        }
        return resultRedirect(reload(payment));
    }

    /** Asks the gateway about {@code valId} and, only if it is a paid transaction that matches THIS payment in
     * tran_id, amount and currency, atomically moves the payment to SUCCESS. */
    private Verdict verify(Payment payment, String valId, Map<String, String> params) {
        SslCommerzClient.Validation v = sslCommerzClient.validate(valId);
        if (v.unavailable()) {
            return Verdict.UNAVAILABLE;
        }
        if (!v.valid()) {
            log.warn("Payment {}: gateway says val_id is not a paid transaction", payment.getTranId());
            return Verdict.REJECTED;
        }
        String problem = PaymentRules.mismatch(payment, v.tranId(), v.amount(), v.currency());
        if (problem == null) {
            problem = PaymentRules.inconsistency(payment);
        }
        if (problem != null) {
            log.warn("Payment {} not confirmed: {}", payment.getTranId(), problem);
            auditService.record(null, "PAYMENT_REJECTED", "PAYMENT", payment.getTranId(), problem, false, null);
            return Verdict.REJECTED;
        }
        String method = firstNonBlank(v.cardType(), params.get("card_type"), params.get("card_issuer"), params.get("gw"));
        boolean won;
        try {
            won = store.claimSuccess(payment.getId(), valId, method);
        } catch (DuplicateKeyException e) {
            // This val_id already confirmed a different payment record: a replay.
            log.warn("Payment {} not confirmed: val_id was already used by another payment", payment.getTranId());
            auditService.record(null, "PAYMENT_REJECTED", "PAYMENT", payment.getTranId(),
                    "val_id already used by another payment", false, null);
            return Verdict.REJECTED;
        }
        if (won) {
            auditService.record(null, "PAYMENT_SUCCESS", "PAYMENT", payment.getTranId(),
                    payment.getPurpose() + " " + payment.getAmount() + " " + payment.getCurrency()
                            + " by " + payment.payerRole() + " " + payment.ownerId(), true, null);
            return Verdict.CONFIRMED;
        }
        // Lost the race: confirmed only if the winner really made it SUCCESS.
        return reload(payment).getStatus() == PaymentStatus.SUCCESS ? Verdict.CONFIRMED : Verdict.REJECTED;
    }

    private Payment reload(Payment payment) {
        return store.findByTranId(payment.getTranId()).orElse(payment);
    }

    private record EffectResult(String note, String message) {
        static EffectResult done(String message) { return new EffectResult(null, message); }
        static EffectResult permanent(String note, String message) { return new EffectResult(note, message); }
    }

    /** Applies the paid-for effect of a SUCCESS payment and records that it is done. Safe to call any number of
     * times, from any number of threads or instances: the effect is idempotent, and only the one caller that
     * atomically flips effectPending true -> false sends the notification. A failure keeps effectPending=true. */
    void finishEffect(String tranId) {
        Payment p = store.findByTranId(tranId).orElse(null);
        if (p == null || p.getStatus() != PaymentStatus.SUCCESS || !p.isEffectPending()) {
            return;
        }
        EffectResult result;
        try {
            result = computeEffect(p);
        } catch (RuntimeException e) {
            log.error("Payment {} was confirmed but its effect could not be applied; it will be retried", p.getTranId(), e);
            store.recordEffectFailure(p.getId(), e.getClass().getSimpleName() + ": " + e.getMessage());
            auditService.record(null, "PAYMENT_EFFECT_FAILED", "PAYMENT", p.getTranId(),
                    p.getPurpose() + " " + p.getAmount() + " " + p.getCurrency(), false, e.getMessage());
            return;
        }
        if (store.completeEffect(p.getId(), result.note())) {
            if (result.note() != null) {
                log.error("Payment {} is paid but cannot be activated: {}", p.getTranId(), result.note());
                auditService.record(null, "PAYMENT_EFFECT_FAILED", "PAYMENT", p.getTranId(),
                        p.getPurpose() + " " + p.getAmount() + " " + p.getCurrency(), false, result.note());
            }
            try {
                notificationService.notify(p.ownerId(), result.message(), linkFor(p));
            } catch (RuntimeException e) {
                log.warn("Payment {}: effect applied but the notification failed: {}", p.getTranId(), e.getMessage());
            }
        }
    }

    /** Where the "payment received" notification leads: the published job, the employer dashboard or Student Pro. */
    static String linkFor(Payment p) {
        if (p.getPurpose() == PaymentPurpose.JOB_POST && p.getJobId() != null) {
            return NotificationLinks.job(p.getJobId());
        }
        return p.getPurpose() == PaymentPurpose.STUDENT_PRO ? NotificationLinks.premium() : NotificationLinks.employerDashboard();
    }

    /** Idempotent: calling it again for the same payment changes nothing. Throws only for retryable problems. */
    private EffectResult computeEffect(Payment payment) {
        String problem = PaymentRules.inconsistency(payment);
        if (problem != null) {
            return EffectResult.permanent("refused to apply an inconsistent payment: " + problem,
                    "Payment received, but it could not be applied automatically. Please contact support and quote "
                            + payment.getTranId() + ".");
        }
        switch (payment.getPurpose()) {
            case JOB_POST -> {
                PaymentStore.JobPublish r = store.publishJob(payment.getJobId());
                if (r == PaymentStore.JobPublish.NOT_PUBLISHABLE) {
                    return EffectResult.permanent("job " + payment.getJobId()
                                    + " is missing or no longer waiting for payment - needs manual review / refund",
                            "Payment received, but we could not publish this job (it was deleted or changed). "
                                    + "Please contact support and quote " + payment.getTranId() + ".");
                }
                return EffectResult.done("Payment received - your job is now live.");
            }
            case SUBSCRIPTION -> {
                store.extendEmployerPro(payment.getEmployerId(), payment.getTranId(), subscriptionDays);
                return EffectResult.done("Payment received - Skill2Job Pro is now active.");
            }
            case STUDENT_PRO -> {
                Instant until = store.extendStudentPro(payment.getStudentId(), payment.getTranId(), studentProDays);
                return EffectResult.done("Payment received - Student Pro is active until "
                        + until.toString().substring(0, 10) + ".");
            }
            default -> throw new IllegalStateException("Unknown payment purpose " + payment.getPurpose());
        }
    }

    // ---------------------------------------------------------------- recovery

    private static final int MAX_EFFECT_ATTEMPTS = 50;
    private static final int RECOVERY_BATCH = 50;

    /** Called on a schedule (PaymentRecoveryScheduler) and safe to run on several instances at once.
     * (1) Finishes payments that were confirmed but whose activation failed or was interrupted.
     * (2) Re-validates val_ids that arrived while the gateway was unreachable.
     * @return how many payments were looked at. */
    public int recover() {
        int looked = 0;
        for (Payment p : store.findEffectsToRetry(Instant.now().minusSeconds(30), MAX_EFFECT_ATTEMPTS, RECOVERY_BATCH)) {
            looked++;
            try {
                finishEffect(p.getTranId());
            } catch (RuntimeException e) {
                log.error("Recovery of payment {} failed: {}", p.getTranId(), e.getMessage());
            }
        }
        for (Payment p : store.findVerificationsToRetry(Instant.now().minusSeconds(2L * 24 * 3600), RECOVERY_BATCH)) {
            looked++;
            try {
                Verdict v = verify(p, p.getPendingValId(), Map.of());
                if (v == Verdict.CONFIRMED) {
                    finishEffect(p.getTranId());
                } else if (v == Verdict.REJECTED) {
                    store.clearPendingVerification(p.getId());
                }
            } catch (RuntimeException e) {
                log.error("Re-validation of payment {} failed: {}", p.getTranId(), e.getMessage());
            }
        }
        return looked;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** "pending" while the payment is confirmed but not yet activated (or needs a look), so the customer is never
     * told "your job is live" when it is not. */
    private String resultRedirect(Payment payment) {
        boolean notActivated = payment.getStatus() == PaymentStatus.SUCCESS
                && (payment.isEffectPending() || payment.getEffectError() != null);
        String status = notActivated ? "pending" : payment.getStatus().name().toLowerCase();
        return baseUrl + "/payment-result.html?status=" + status
                + "&purpose=" + payment.getPurpose().name().toLowerCase()
                + "&tran_id=" + payment.getTranId();
    }

    /** The caller's own billing history. Employers and students each see only the payments of their own
     * role and id - the id comes from the login token, never from the request. */
    public List<PaymentResponse> myPayments(AuthUser user) {
        List<Payment> payments;
        if (user.role() == Role.STUDENT) {
            payments = paymentRepository.findByStudentIdOrderByCreatedAtDesc(user.id());
        } else if (user.role() == Role.EMPLOYER) {
            payments = paymentRepository.findByEmployerIdOrderByCreatedAtDesc(user.id());
        } else {
            throw ApiException.forbidden("Admins have no billing history of their own. See the Payments tab instead.");
        }
        return payments.stream().map(PaymentResponse::from).toList();
    }
}
