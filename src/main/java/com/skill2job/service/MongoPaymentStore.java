package com.skill2job.service;

import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Job;
import com.skill2job.entity.Payment;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.SubscriptionPlan;
import com.skill2job.repository.PaymentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** MongoDB implementation of {@link PaymentStore}: single-document conditional updates only (no transactions). */
@Component
public class MongoPaymentStore implements PaymentStore {

    private static final int EXTEND_RETRIES = 8;
    private static final List<String> CLAIMABLE = PaymentRules.CLAIMABLE.stream().map(Enum::name).toList();

    private final PaymentRepository paymentRepository;
    private final MongoTemplate mongo;

    public MongoPaymentStore(PaymentRepository paymentRepository, MongoTemplate mongo) {
        this.paymentRepository = paymentRepository;
        this.mongo = mongo;
    }

    @Override
    public Optional<Payment> findByTranId(String tranId) {
        return paymentRepository.findByTranId(tranId);
    }

    @Override
    public boolean claimSuccess(String paymentId, String valId, String method) {
        Query q = Query.query(Criteria.where("_id").is(paymentId).and("status").in(CLAIMABLE));
        Update u = new Update().set("status", PaymentStatus.SUCCESS.name()).set("valId", valId)
                .set("paymentMethod", method).set("paidAt", Instant.now()).set("effectPending", true)
                .unset("pendingValId");
        return mongo.updateFirst(q, u, Payment.class).getModifiedCount() == 1;
    }

    @Override
    public boolean markFailedOrCancelled(String paymentId, PaymentStatus status) {
        Query q = Query.query(Criteria.where("_id").is(paymentId).and("status").is(PaymentStatus.PENDING.name()));
        return mongo.updateFirst(q, new Update().set("status", status.name()), Payment.class).getModifiedCount() == 1;
    }

    @Override
    public void recordPendingVerification(String paymentId, String valId) {
        Query q = Query.query(Criteria.where("_id").is(paymentId).and("status").in(CLAIMABLE));
        mongo.updateFirst(q, new Update().set("pendingValId", valId), Payment.class);
    }

    @Override
    public void clearPendingVerification(String paymentId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(paymentId)), new Update().unset("pendingValId"), Payment.class);
    }

    @Override
    public boolean completeEffect(String paymentId, String note) {
        Query q = Query.query(Criteria.where("_id").is(paymentId)
                .and("status").is(PaymentStatus.SUCCESS.name()).and("effectPending").is(true));
        Update u = new Update().set("effectPending", false).set("effectAppliedAt", Instant.now()).set("effectError", note);
        return mongo.updateFirst(q, u, Payment.class).getModifiedCount() == 1;
    }

    @Override
    public void recordEffectFailure(String paymentId, String error) {
        String shortError = error == null ? "unknown error" : (error.length() > 300 ? error.substring(0, 300) : error);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(paymentId)),
                new Update().inc("effectAttempts", 1).set("effectError", shortError), Payment.class);
    }

    @Override
    public JobPublish publishJob(String jobId) {
        Query q = Query.query(Criteria.where("_id").is(jobId).and("status").is(JobStatus.PENDING_PAYMENT.name()));
        Update u = new Update().set("status", JobStatus.OPEN.name()).set("publishedAt", Instant.now());
        if (mongo.updateFirst(q, u, Job.class).getModifiedCount() == 1) {
            return JobPublish.PUBLISHED;
        }
        Job job = mongo.findById(jobId, Job.class);
        if (job != null && job.getStatus() == JobStatus.OPEN) {
            return JobPublish.ALREADY_OPEN;
        }
        return JobPublish.NOT_PUBLISHABLE;
    }

    @Override
    public Instant extendEmployerPro(String employerId, String tranId, int days) {
        return extend(EmployerProfile.class, employerId, tranId, days, "subscriptionExpiresAt",
                () -> {
                    EmployerProfile p = new EmployerProfile();
                    p.setUserId(employerId);
                    return p;
                },
                p -> p.isProActive() ? p.getSubscriptionExpiresAt() : null,
                EmployerProfile::getSubscriptionExpiresAt,
                EmployerProfile::getAppliedPaymentIds,
                u -> u.set("subscriptionPlan", SubscriptionPlan.PRO.name()));
    }

    @Override
    public Instant extendStudentPro(String studentId, String tranId, int days) {
        return extend(StudentProfile.class, studentId, tranId, days, "proExpiresAt",
                () -> {
                    StudentProfile p = new StudentProfile();
                    p.setUserId(studentId);
                    return p;
                },
                p -> p.getProExpiresAt(),
                StudentProfile::getProExpiresAt,
                StudentProfile::getAppliedPaymentIds,
                u -> { });
    }

    /**
     * Exactly-once extension without transactions. The new expiry and the "this payment was applied" marker are
     * written by ONE conditional update on ONE document, and the condition says (a) the marker is not there yet
     * and (b) the expiry is still what we read. So: a repeat call for the same payment finds the marker and does
     * nothing; two racing calls for the same payment - only one update matches; a racing call for a DIFFERENT
     * payment changes the expiry, our condition fails, and we re-read and try again on top of the new value.
     */
    private <T> Instant extend(Class<T> type, String userId, String tranId, int days, String expiryField,
                               Supplier<T> blank, Function<T, Instant> activeExpiry, Function<T, Instant> currentExpiry,
                               Function<T, List<String>> applied, java.util.function.Consumer<Update> extraSet) {
        Query byUser = Query.query(Criteria.where("userId").is(userId));
        if (mongo.findOne(byUser, type) == null) {
            // The payer may have paid before ever opening their profile page: create it rather than lose the payment.
            try {
                mongo.insert(blank.get());
            } catch (DuplicateKeyException ignored) {
                // created concurrently (unique index on userId) - fine, read it below
            }
        }
        for (int attempt = 0; attempt < EXTEND_RETRIES; attempt++) {
            T profile = mongo.findOne(byUser, type);
            if (profile == null) {
                continue;
            }
            List<String> done = applied.apply(profile);
            if (done != null && done.contains(tranId)) {
                return currentExpiry.apply(profile);
            }
            Instant observed = activeExpiry.apply(profile);
            Instant stored = currentExpiry.apply(profile);
            Instant now = Instant.now();
            Instant next = PaymentRules.extendFrom(observed, now, days);

            Criteria c = Criteria.where("userId").is(userId).and("appliedPaymentIds").ne(tranId)
                    .and(expiryField).is(stored);
            Update u = new Update().set(expiryField, next).addToSet("appliedPaymentIds", tranId);
            extraSet.accept(u);
            if (mongo.updateFirst(Query.query(c), u, type).getModifiedCount() == 1) {
                return next;
            }
        }
        throw new IllegalStateException("Could not extend the plan for payment " + tranId + " (too much contention); will retry");
    }

    @Override
    public List<Payment> findEffectsToRetry(Instant paidBefore, int maxAttempts, int limit) {
        Query q = Query.query(Criteria.where("status").is(PaymentStatus.SUCCESS.name())
                .and("effectPending").is(true).and("paidAt").lt(paidBefore).and("effectAttempts").lt(maxAttempts))
                .limit(limit);
        return mongo.find(q, Payment.class);
    }

    @Override
    public List<Payment> findVerificationsToRetry(Instant createdAfter, int limit) {
        Query q = Query.query(Criteria.where("status").in(CLAIMABLE)
                .and("pendingValId").ne(null).and("createdAt").gt(createdAfter)).limit(limit);
        return mongo.find(q, Payment.class);
    }
}
