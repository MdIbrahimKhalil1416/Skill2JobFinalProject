package com.skill2job.service;

import com.skill2job.entity.Payment;
import com.skill2job.enums.PaymentStatus;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Test double for {@link PaymentStore}. Every method is atomic (synchronized) exactly like the single-document
 * conditional updates of MongoPaymentStore, and it hands out COPIES of payments like a database would, so the
 * service under test never sees another thread's half-finished work. Failure hooks simulate a database that
 * breaks half-way through an activation. (This proves PaymentService's logic given atomic primitives; the Mongo
 * queries themselves are NOT exercised here.)
 */
class InMemoryPaymentStore implements PaymentStore {

    final Map<String, Payment> payments = new LinkedHashMap<>();
    /** jobId -> status name ("PENDING_PAYMENT", "OPEN", "CLOSED"). A missing key is a deleted job. */
    final Map<String, String> jobs = new HashMap<>();
    final Map<String, Instant> employerExpiry = new HashMap<>();
    final Map<String, Set<String>> employerApplied = new HashMap<>();
    final Map<String, Instant> studentExpiry = new HashMap<>();
    final Map<String, Set<String>> studentApplied = new HashMap<>();

    volatile int failExtends;        // the next N extend calls throw BEFORE changing anything
    volatile int failAfterExtend;    // the next N extend calls change the profile and THEN throw (crash after the write)
    volatile int failPublish;
    volatile long latencyMs;         // widens the window in which racing threads interleave

    synchronized void add(Payment p) {
        if (p.getId() == null) {
            p.setId("id-" + p.getTranId());
        }
        payments.put(p.getId(), copy(p));
    }

    synchronized Payment get(String tranId) {
        return payments.values().stream().filter(p -> p.getTranId().equals(tranId)).findFirst().map(InMemoryPaymentStore::copy).orElseThrow();
    }

    synchronized void backdatePaidAt(String tranId) {
        payments.values().stream().filter(p -> p.getTranId().equals(tranId)).forEach(p -> p.setPaidAt(Instant.now().minusSeconds(3600)));
    }

    private static Payment copy(Payment s) {
        Payment p = new Payment();
        p.setId(s.getId()); p.setEmployerId(s.getEmployerId()); p.setStudentId(s.getStudentId());
        p.setPayerRoleValue(s.getPayerRoleValue()); p.setTranId(s.getTranId()); p.setPurpose(s.getPurpose());
        p.setJobId(s.getJobId()); p.setPlan(s.getPlan()); p.setAmount(s.getAmount()); p.setCurrency(s.getCurrency());
        p.setStatus(s.getStatus()); p.setValId(s.getValId()); p.setPaymentMethod(s.getPaymentMethod());
        p.setCreatedAt(s.getCreatedAt()); p.setPaidAt(s.getPaidAt()); p.setEffectPending(s.isEffectPending());
        p.setEffectAppliedAt(s.getEffectAppliedAt()); p.setEffectError(s.getEffectError());
        p.setEffectAttempts(s.getEffectAttempts()); p.setPendingValId(s.getPendingValId());
        return p;
    }

    private void pause() {
        if (latencyMs > 0) {
            try {
                Thread.sleep(latencyMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public synchronized Optional<Payment> findByTranId(String tranId) {
        return payments.values().stream().filter(p -> tranId.equals(p.getTranId())).findFirst().map(InMemoryPaymentStore::copy);
    }

    @Override
    public synchronized boolean claimSuccess(String paymentId, String valId, String method) {
        Payment p = payments.get(paymentId);
        if (p == null || !PaymentRules.CLAIMABLE.contains(p.getStatus())) {
            return false;
        }
        boolean used = payments.values().stream().anyMatch(o -> valId.equals(o.getValId()));
        if (used) {
            throw new DuplicateKeyException("valId already used");   // the sparse unique index
        }
        p.setStatus(PaymentStatus.SUCCESS);
        p.setValId(valId);
        p.setPaymentMethod(method);
        p.setPaidAt(Instant.now());
        p.setEffectPending(true);
        p.setPendingValId(null);
        return true;
    }

    @Override
    public synchronized boolean markFailedOrCancelled(String paymentId, PaymentStatus status) {
        Payment p = payments.get(paymentId);
        if (p == null || p.getStatus() != PaymentStatus.PENDING) {
            return false;
        }
        p.setStatus(status);
        return true;
    }

    @Override
    public synchronized void recordPendingVerification(String paymentId, String valId) {
        Payment p = payments.get(paymentId);
        if (p != null && PaymentRules.CLAIMABLE.contains(p.getStatus())) {
            p.setPendingValId(valId);
        }
    }

    @Override
    public synchronized void clearPendingVerification(String paymentId) {
        Payment p = payments.get(paymentId);
        if (p != null) {
            p.setPendingValId(null);
        }
    }

    @Override
    public synchronized boolean completeEffect(String paymentId, String note) {
        Payment p = payments.get(paymentId);
        if (p == null || p.getStatus() != PaymentStatus.SUCCESS || !p.isEffectPending()) {
            return false;
        }
        p.setEffectPending(false);
        p.setEffectAppliedAt(Instant.now());
        p.setEffectError(note);
        return true;
    }

    @Override
    public synchronized void recordEffectFailure(String paymentId, String error) {
        Payment p = payments.get(paymentId);
        if (p != null) {
            p.setEffectAttempts(p.getEffectAttempts() + 1);
            p.setEffectError(error);
        }
    }

    @Override
    public JobPublish publishJob(String jobId) {
        pause();
        synchronized (this) {
            if (failPublish > 0) {
                failPublish--;
                throw new IllegalStateException("simulated database failure");
            }
            String status = jobs.get(jobId);
            if ("PENDING_PAYMENT".equals(status)) {
                jobs.put(jobId, "OPEN");
                return JobPublish.PUBLISHED;
            }
            return "OPEN".equals(status) ? JobPublish.ALREADY_OPEN : JobPublish.NOT_PUBLISHABLE;
        }
    }

    @Override
    public Instant extendEmployerPro(String employerId, String tranId, int days) {
        return extend(employerExpiry, employerApplied, employerId, tranId, days);
    }

    @Override
    public Instant extendStudentPro(String studentId, String tranId, int days) {
        return extend(studentExpiry, studentApplied, studentId, tranId, days);
    }

    private Instant extend(Map<String, Instant> expiry, Map<String, Set<String>> applied, String id, String tranId, int days) {
        pause();
        synchronized (this) {
            if (failExtends > 0) {
                failExtends--;
                throw new IllegalStateException("simulated database failure");
            }
            Set<String> done = applied.computeIfAbsent(id, k -> new HashSet<>());
            if (done.contains(tranId)) {
                return expiry.get(id);
            }
            Instant next = PaymentRules.extendFrom(expiry.get(id), Instant.now(), days);
            expiry.put(id, next);
            done.add(tranId);
            if (failAfterExtend > 0) {
                failAfterExtend--;
                throw new IllegalStateException("simulated crash after the plan was extended");
            }
            return next;
        }
    }

    @Override
    public synchronized List<Payment> findEffectsToRetry(Instant paidBefore, int maxAttempts, int limit) {
        List<Payment> out = new ArrayList<>();
        for (Payment p : payments.values()) {
            if (p.getStatus() == PaymentStatus.SUCCESS && p.isEffectPending() && p.getPaidAt() != null
                    && p.getPaidAt().isBefore(paidBefore) && p.getEffectAttempts() < maxAttempts && out.size() < limit) {
                out.add(copy(p));
            }
        }
        return out;
    }

    @Override
    public synchronized List<Payment> findVerificationsToRetry(Instant createdAfter, int limit) {
        List<Payment> out = new ArrayList<>();
        for (Payment p : payments.values()) {
            if (PaymentRules.CLAIMABLE.contains(p.getStatus()) && p.getPendingValId() != null
                    && p.getCreatedAt() != null && p.getCreatedAt().isAfter(createdAfter) && out.size() < limit) {
                out.add(copy(p));
            }
        }
        return out;
    }
}
