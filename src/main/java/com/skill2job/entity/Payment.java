package com.skill2job.entity;

import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;
import com.skill2job.enums.SubscriptionPlan;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One SSLCommerz payment attempt: a one-time job-posting fee (purpose=JOB_POST, jobId set), an employer
 * Pro subscription (purpose=SUBSCRIPTION, plan set) or a Student Pro purchase (purpose=STUDENT_PRO,
 * studentId set - employerId stays null). Created PENDING when the
 * checkout session is opened, then moved to SUCCESS/FAILED/CANCELLED once SSLCommerz redirects
 * back and (for SUCCESS) the Order Validation API confirms it server-side - see PaymentService.
 */
@Document(collection = "payments")
public class Payment {

    @Id
    private String id;

    /** Set for employer payments (JOB_POST, SUBSCRIPTION); null for student payments. */
    @Indexed
    private String employerId;

    /** Set for student payments (STUDENT_PRO); null for employer payments. */
    @Indexed
    private String studentId;

    /** Which kind of account is paying. Null on payments created before student payments existed -
     * {@link #payerRole()} falls back to the role implied by the purpose for those. */
    private Role payerRoleValue;

    /** Our own unique transaction id, generated before the SSLCommerz session is created and used
     * to look the payment back up when SSLCommerz redirects to success/fail/cancel/ipn. */
    @Indexed(unique = true)
    private String tranId;

    private PaymentPurpose purpose;

    /** Set only for purpose=JOB_POST: the job this fee publishes. */
    private String jobId;

    /** Set only for purpose=SUBSCRIPTION: the plan being purchased. */
    private SubscriptionPlan plan;

    private double amount;
    private String currency = "BDT";
    private PaymentStatus status = PaymentStatus.PENDING;

    /** SSLCommerz's own validation id, set once the payment is confirmed via the Validation API. The sparse
     * unique index means one gateway transaction can confirm at most ONE payment record, so a val_id that
     * was already used can never be replayed against a second payment. */
    @Indexed(unique = true, sparse = true)
    private String valId;
    /** How the customer paid (card brand, bKash, Nagad, ...), from SSLCommerz's callback fields. */
    private String paymentMethod;

    private Instant createdAt;
    private Instant paidAt;

    /** True from the moment a payment is confirmed until its effect (opening the job / extending the plan)
     * has been applied. Lets a later duplicate callback finish the job if the first one died half-way,
     * while never applying it twice. Payments confirmed before this field existed are false (already done). */
    private boolean effectPending;

    /** When the paid-for effect was applied (job opened / plan extended). Null until then. */
    private Instant effectAppliedAt;
    /** Why the effect is not (fully) applied: the last retryable error while effectPending is still true, or a
     * permanent reason (e.g. the job was deleted) once effectPending is false. Null for a clean activation. */
    private String effectError;
    /** How many times applying the effect has failed; the recovery job stops retrying after a cap. */
    private int effectAttempts;
    /** A val_id received while the gateway could not be reached to validate it. Kept so the recovery job can
     * validate it later; cleared once the payment is confirmed or the gateway rejects it. */
    private String pendingValId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmployerId() { return employerId; }
    public void setEmployerId(String employerId) { this.employerId = employerId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public Role getPayerRoleValue() { return payerRoleValue; }
    public void setPayerRoleValue(Role payerRoleValue) { this.payerRoleValue = payerRoleValue; }
    public boolean isEffectPending() { return effectPending; }
    public void setEffectPending(boolean effectPending) { this.effectPending = effectPending; }
    public Instant getEffectAppliedAt() { return effectAppliedAt; }
    public void setEffectAppliedAt(Instant effectAppliedAt) { this.effectAppliedAt = effectAppliedAt; }
    public String getEffectError() { return effectError; }
    public void setEffectError(String effectError) { this.effectError = effectError; }
    public int getEffectAttempts() { return effectAttempts; }
    public void setEffectAttempts(int effectAttempts) { this.effectAttempts = effectAttempts; }
    public String getPendingValId() { return pendingValId; }
    public void setPendingValId(String pendingValId) { this.pendingValId = pendingValId; }

    /** The role that paid: the stored one, or - for older documents - the one implied by the purpose. */
    public Role payerRole() {
        if (payerRoleValue != null) {
            return payerRoleValue;
        }
        return purpose == null ? null : purpose.payerRole();
    }

    /** The user id of whoever paid (employer or student). */
    public String ownerId() {
        return payerRole() == Role.STUDENT ? studentId : employerId;
    }

    public String getTranId() { return tranId; }
    public void setTranId(String tranId) { this.tranId = tranId; }
    public PaymentPurpose getPurpose() { return purpose; }
    public void setPurpose(PaymentPurpose purpose) { this.purpose = purpose; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public SubscriptionPlan getPlan() { return plan; }
    public void setPlan(SubscriptionPlan plan) { this.plan = plan; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public String getValId() { return valId; }
    public void setValId(String valId) { this.valId = valId; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
}
