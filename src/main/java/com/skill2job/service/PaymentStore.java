package com.skill2job.service;

import com.skill2job.entity.Payment;
import com.skill2job.enums.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Every database step the payment callback logic needs, each one a SINGLE atomic, conditional operation.
 * PaymentService decides what to do; this port is what makes each step safe under concurrent callbacks.
 *
 * Why not Mongo multi-document transactions: they need a replica set, and this project runs on both Atlas
 * (replica set) and a plain standalone "mongo:7" in docker-compose, where they would fail. Instead every
 * business effect is made idempotent on its own document (see extendEmployerPro / extendStudentPro), so any
 * step can be retried or run twice concurrently without double effects.
 */
public interface PaymentStore {

    enum JobPublish { PUBLISHED, ALREADY_OPEN, NOT_PUBLISHABLE }

    Optional<Payment> findByTranId(String tranId);

    /** Atomically PENDING/FAILED/CANCELLED -> SUCCESS (sets effectPending=true). True for exactly one of any
     * number of concurrent callers. Throws DuplicateKeyException if this val_id already confirmed another payment. */
    boolean claimSuccess(String paymentId, String valId, String method);

    /** Atomically PENDING -> FAILED/CANCELLED. Never touches a payment in any other state. */
    boolean markFailedOrCancelled(String paymentId, PaymentStatus status);

    /** Remember a val_id that could not be validated because the gateway was unreachable (non-final states only). */
    void recordPendingVerification(String paymentId, String valId);

    void clearPendingVerification(String paymentId);

    /** Atomically effectPending true -> false (+ effectAppliedAt, effectError=note). True for exactly one caller. */
    boolean completeEffect(String paymentId, String note);

    /** Keeps effectPending=true, counts the attempt and remembers the error so the recovery job retries it. */
    void recordEffectFailure(String paymentId, String error);

    /** PENDING_PAYMENT -> OPEN, atomically. Idempotent: an already OPEN job reports ALREADY_OPEN. */
    JobPublish publishJob(String jobId);

    /** Extends the employer's Pro plan by {@code days} for payment {@code tranId} AT MOST ONCE (a repeat call for
     * the same tranId changes nothing). Returns the expiry in force afterwards. */
    Instant extendEmployerPro(String employerId, String tranId, int days);

    /** Same exactly-once contract for Student Pro. */
    Instant extendStudentPro(String studentId, String tranId, int days);

    /** SUCCESS payments whose effect is still pending, paid before {@code paidBefore}, with fewer than maxAttempts failures. */
    List<Payment> findEffectsToRetry(Instant paidBefore, int maxAttempts, int limit);

    /** Not-yet-successful payments holding a val_id that still needs validating, created after {@code createdAfter}. */
    List<Payment> findVerificationsToRetry(Instant createdAfter, int limit);
}
