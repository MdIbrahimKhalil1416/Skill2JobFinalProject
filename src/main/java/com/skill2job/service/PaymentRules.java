package com.skill2job.service;

import com.skill2job.entity.Payment;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * The pure decision rules of the payment module (no database, no HTTP), kept apart from PaymentService
 * so they can be unit tested exhaustively - see PaymentRulesTest.
 */
public final class PaymentRules {

    /** Statuses from which a confirmed SSLCommerz payment may still become SUCCESS. A failed or cancelled
     * attempt can legitimately be paid later (e.g. the customer retries inside the same gateway page, or an
     * IPN arrives after a fail redirect), but a SUCCESS never moves. */
    public static final Set<PaymentStatus> CLAIMABLE = Set.of(PaymentStatus.PENDING, PaymentStatus.FAILED, PaymentStatus.CANCELLED);

    private PaymentRules() { }

    public static boolean canBecomeSuccess(PaymentStatus status) {
        return status != null && CLAIMABLE.contains(status);
    }

    /** A failure/cancel callback may only move a payment that is still PENDING - it must never overwrite a
     * payment that already succeeded (or flip FAILED to CANCELLED). */
    public static boolean canBecomeFailedOrCancelled(PaymentStatus status) {
        return status == PaymentStatus.PENDING;
    }

    /**
     * Compares what SSLCommerz's Validation API says about a val_id with our own Payment.
     * @return null when it is THIS payment (same tran_id, same currency, same amount to the paisa),
     *         otherwise a short reason for the log. A val_id from a different, cheaper transaction must
     *         never be accepted for this one.
     */
    public static String mismatch(Payment payment, String tranId, String amount, String currency) {
        if (payment.getTranId() == null || !payment.getTranId().equals(tranId)) {
            return "val_id belongs to tran_id " + tranId + ", not " + payment.getTranId();
        }
        if (currency == null || currency.isBlank()) {
            return "validation had no currency";   // never accept an amount whose currency we cannot compare
        }
        if (!payment.getCurrency().equalsIgnoreCase(currency.trim())) {
            return "currency mismatch: gateway " + currency + ", expected " + payment.getCurrency();
        }
        double paid;
        try {
            paid = Double.parseDouble(amount.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return "validation had no readable amount";
        }
        if (Math.abs(paid - payment.getAmount()) > 0.009) {
            return "amount mismatch: paid " + paid + ", expected " + payment.getAmount();
        }
        return null;
    }

    /**
     * Guards against mixing employer and student payments. A payment is consistent when its purpose
     * belongs to the role that paid, the owner id of that role is set, and the other role's id is not.
     * @return null when consistent, otherwise the reason.
     */
    public static String inconsistency(Payment p) {
        if (p.getPurpose() == null) {
            return "payment has no purpose";
        }
        Role role = p.payerRole();
        if (role != p.getPurpose().payerRole()) {
            return "purpose " + p.getPurpose() + " belongs to " + p.getPurpose().payerRole() + " but payer is " + role;
        }
        boolean hasEmployer = p.getEmployerId() != null && !p.getEmployerId().isBlank();
        boolean hasStudent = p.getStudentId() != null && !p.getStudentId().isBlank();
        if (role == Role.STUDENT && (!hasStudent || hasEmployer)) {
            return "a student payment must carry a studentId and no employerId";
        }
        if (role == Role.EMPLOYER && (!hasEmployer || hasStudent)) {
            return "an employer payment must carry an employerId and no studentId";
        }
        if (p.getPurpose() == PaymentPurpose.JOB_POST && (p.getJobId() == null || p.getJobId().isBlank())) {
            return "a job-post payment must carry a jobId";
        }
        return null;
    }

    /** New expiry for a purchase: a renewal while still active extends from the current expiry, so the
     * remaining days are never lost; an expired or first purchase starts from now. */
    public static Instant extendFrom(Instant currentExpiry, Instant now, int days) {
        Instant base = currentExpiry != null && currentExpiry.isAfter(now) ? currentExpiry : now;
        return base.plus(days, ChronoUnit.DAYS);
    }
}
