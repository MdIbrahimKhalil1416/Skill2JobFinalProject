package com.skill2job.service;

import com.skill2job.audit.AuditService;
import com.skill2job.entity.Payment;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;
import com.skill2job.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PaymentService callback / activation / recovery behaviour, with a mocked gateway (no real payment, no network)
 * and an in-memory store with the same atomic semantics as MongoPaymentStore.
 */
class PaymentServiceTest {

    /** Scripted SSLCommerz: val_id -> what the Validation API would answer. Never touches the network. */
    static class FakeGateway extends SslCommerzClient {
        final Map<String, Validation> answers = new HashMap<>();
        final AtomicInteger validateCalls = new AtomicInteger();
        volatile boolean down;

        FakeGateway() { super(null); }

        void confirms(String valId, String tranId, String amount, String currency) {
            answers.put(valId, Validation.paid(tranId, amount, currency, "VISA"));
        }

        @Override
        public Validation validate(String valId) {
            validateCalls.incrementAndGet();
            if (down) {
                return Validation.gatewayDown();
            }
            return answers.getOrDefault(valId, Validation.invalid());
        }
    }

    static class RecordingNotifier extends NotificationService {
        final List<String> sent = Collections.synchronizedList(new ArrayList<>());
        final List<String> links = Collections.synchronizedList(new ArrayList<>());
        RecordingNotifier() { super(null); }
        @Override
        public void notify(String userId, String message, String link) { sent.add(userId + ": " + message); links.add(link); }
    }

    static class RecordingAudit extends AuditService {
        final List<String> actions = Collections.synchronizedList(new ArrayList<>());
        RecordingAudit() { super(null, null); }
        @Override
        public void record(AuthUser actor, String action, String targetType, String targetId, String details,
                           boolean success, String error) { actions.add(action); }
        long count(String action) { return actions.stream().filter(action::equals).count(); }
    }

    private InMemoryPaymentStore store;
    private FakeGateway gateway;
    private RecordingNotifier notifier;
    private RecordingAudit audit;
    private PaymentService service;

    @BeforeEach
    void setUp() throws Exception {
        store = new InMemoryPaymentStore();
        gateway = new FakeGateway();
        notifier = new RecordingNotifier();
        audit = new RecordingAudit();
        service = new PaymentService(null, null, notifier, gateway, audit, store, null);
        set("subscriptionDays", 30);
        set("studentProDays", 30);
        set("baseUrl", "http://app");
    }

    private void set(String field, Object value) throws Exception {
        Field f = PaymentService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }

    // ------------------------------------------------------------ fixtures

    private Payment subscription(String tranId) {
        Payment p = new Payment();
        p.setEmployerId("emp-1");
        p.setPayerRoleValue(Role.EMPLOYER);
        p.setPurpose(PaymentPurpose.SUBSCRIPTION);
        p.setTranId(tranId);
        p.setAmount(2000);
        p.setCreatedAt(Instant.now());
        store.add(p);
        return p;
    }

    private Payment jobFee(String tranId, String jobId) {
        Payment p = new Payment();
        p.setEmployerId("emp-1");
        p.setPayerRoleValue(Role.EMPLOYER);
        p.setPurpose(PaymentPurpose.JOB_POST);
        p.setJobId(jobId);
        p.setTranId(tranId);
        p.setAmount(500);
        p.setCreatedAt(Instant.now());
        store.add(p);
        store.jobs.put(jobId, "PENDING_PAYMENT");
        return p;
    }

    private Payment studentPro(String tranId) {
        Payment p = new Payment();
        p.setStudentId("stu-1");
        p.setPayerRoleValue(Role.STUDENT);
        p.setPurpose(PaymentPurpose.STUDENT_PRO);
        p.setTranId(tranId);
        p.setAmount(299);
        p.setCreatedAt(Instant.now());
        store.add(p);
        return p;
    }

    private static Map<String, String> callback(String tranId, String valId) {
        Map<String, String> m = new HashMap<>();
        m.put("tran_id", tranId);
        if (valId != null) {
            m.put("val_id", valId);
        }
        return m;
    }

    private static void assertAboutOnePeriod(Instant expiry, int days) {
        assertNotNull(expiry);
        long hours = Duration.between(Instant.now(), expiry).toHours();
        assertTrue(hours >= days * 24L - 2 && hours <= days * 24L + 2, "expected ~" + days + " days from now but was " + hours + "h");
    }

    // ------------------------------------------------------------ successful processing

    @Test
    void verifiedSubscriptionPaymentActivatesProOnce() {
        subscription("SUB-1");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");

        String redirect = service.handleSuccess(callback("SUB-1", "V1"));

        Payment p = store.get("SUB-1");
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        assertEquals("V1", p.getValId());
        assertFalse(p.isEffectPending());
        assertNotNull(p.getEffectAppliedAt());
        assertNull(p.getEffectError());
        assertAboutOnePeriod(store.employerExpiry.get("emp-1"), 30);
        assertTrue(redirect.contains("status=success"), redirect);
        assertEquals(1, notifier.sent.size());
        assertEquals(1, audit.count("PAYMENT_SUCCESS"));
    }

    @Test
    void verifiedJobFeeOpensTheJobAndStudentProExtendsTheStudent() {
        jobFee("JOB-1", "job-1");
        gateway.confirms("VJ", "JOB-1", "500.00", "BDT");
        assertTrue(service.handleSuccess(callback("JOB-1", "VJ")).contains("status=success"));
        assertEquals("OPEN", store.jobs.get("job-1"));

        studentPro("STU-1");
        gateway.confirms("VS", "STU-1", "299.00", "BDT");
        assertTrue(service.handleSuccess(callback("STU-1", "VS")).contains("status=success"));
        assertAboutOnePeriod(store.studentExpiry.get("stu-1"), 30);
        assertNull(store.employerExpiry.get("stu-1"), "a student payment must never touch an employer plan");
    }

    // ------------------------------------------------------------ duplicate callbacks

    @Test
    void duplicateCallbacksAreHarmlessAndNeverExtendTwice() {
        subscription("SUB-1");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");

        service.handleSuccess(callback("SUB-1", "V1"));
        Instant first = store.employerExpiry.get("emp-1");
        service.handleSuccess(callback("SUB-1", "V1"));
        service.handleIpn(callback("SUB-1", "V1"));
        String again = service.handleSuccess(callback("SUB-1", "V1"));

        assertEquals(first, store.employerExpiry.get("emp-1"));
        assertEquals(1, store.employerApplied.get("emp-1").size());
        assertEquals(1, notifier.sent.size());
        assertEquals(1, audit.count("PAYMENT_SUCCESS"));
        assertTrue(again.contains("status=success"));
        assertEquals(1, gateway.validateCalls.get(), "a payment already confirmed must not be re-validated on every duplicate");
    }

    @Test
    void failOrCancelCallbackNeverOverwritesASuccess() {
        subscription("SUB-1");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        service.handleSuccess(callback("SUB-1", "V1"));

        String r1 = service.handleFail(callback("SUB-1", null));
        String r2 = service.handleCancel(callback("SUB-1", null));

        assertEquals(PaymentStatus.SUCCESS, store.get("SUB-1").getStatus());
        assertTrue(r1.contains("status=success") && r2.contains("status=success"));
    }

    @Test
    void aFailedAttemptCanStillBePaidLater() {
        subscription("SUB-1");
        service.handleFail(callback("SUB-1", null));
        assertEquals(PaymentStatus.FAILED, store.get("SUB-1").getStatus());

        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        service.handleIpn(callback("SUB-1", "V1"));

        assertEquals(PaymentStatus.SUCCESS, store.get("SUB-1").getStatus());
        assertAboutOnePeriod(store.employerExpiry.get("emp-1"), 30);
    }

    // ------------------------------------------------------------ concurrency

    @Test
    void browserCallbackAndIpnArrivingTogetherActivateExactlyOnce() throws Exception {
        subscription("SUB-1");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        store.latencyMs = 3;   // make the threads really interleave

        int threads = 24;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final boolean ipn = i % 2 == 0;
            results.add(pool.submit(() -> {
                go.await();
                Map<String, String> params = callback("SUB-1", "V1");
                if (ipn) {
                    service.handleIpn(params);
                    return "ipn";
                }
                return service.handleSuccess(params);
            }));
        }
        go.countDown();
        for (Future<String> f : results) {
            f.get(20, TimeUnit.SECONDS);   // no callback may blow up
        }
        pool.shutdownNow();

        Payment p = store.get("SUB-1");
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        assertFalse(p.isEffectPending());
        assertEquals(1, store.employerApplied.get("emp-1").size(), "plan extended more than once");
        assertAboutOnePeriod(store.employerExpiry.get("emp-1"), 30);
        assertEquals(1, notifier.sent.size(), "exactly one notification");
        assertEquals(1, audit.count("PAYMENT_SUCCESS"), "exactly one success record");
    }

    @Test
    void concurrentCallbacksAndRecoveryNeverDoubleTheJobOrTheStudentPlan() throws Exception {
        studentPro("STU-1");
        gateway.confirms("VS", "STU-1", "299.00", "BDT");
        store.latencyMs = 2;

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int n = i;
            fs.add(pool.submit(() -> {
                go.await();
                if (n % 4 == 0) {
                    service.recover();
                } else {
                    service.handleSuccess(callback("STU-1", "VS"));
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) {
            f.get(20, TimeUnit.SECONDS);
        }
        pool.shutdownNow();

        assertEquals(1, store.studentApplied.get("stu-1").size());
        assertAboutOnePeriod(store.studentExpiry.get("stu-1"), 30);
        assertEquals(1, notifier.sent.size());
    }

    // ------------------------------------------------------------ failed activation, retry, recovery

    @Test
    void activationFailureLeavesAVisibleRetryableStateAndTheNextCallbackFinishesIt() {
        subscription("SUB-1");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        store.failExtends = 1;

        String first = service.handleSuccess(callback("SUB-1", "V1"));

        Payment p = store.get("SUB-1");
        assertEquals(PaymentStatus.SUCCESS, p.getStatus(), "the money was verified, so the payment is SUCCESS");
        assertTrue(p.isEffectPending(), "but the activation is visibly unfinished");
        assertEquals(1, p.getEffectAttempts());
        assertNotNull(p.getEffectError());
        assertNull(store.employerExpiry.get("emp-1"));
        assertTrue(first.contains("status=pending"), "the customer must not be told it is live: " + first);
        assertEquals(0, notifier.sent.size());
        assertEquals(1, audit.count("PAYMENT_EFFECT_FAILED"));

        service.handleIpn(callback("SUB-1", "V1"));   // the gateway's retry of the IPN
        Payment done = store.get("SUB-1");
        assertFalse(done.isEffectPending());
        assertAboutOnePeriod(store.employerExpiry.get("emp-1"), 30);
        assertEquals(1, notifier.sent.size());
        assertTrue(service.handleSuccess(callback("SUB-1", "V1")).contains("status=success"));
    }

    @Test
    void recoveryJobFinishesAConfirmedPaymentWhoseActivationFailed() {
        jobFee("JOB-1", "job-1");
        gateway.confirms("VJ", "JOB-1", "500.00", "BDT");
        store.failPublish = 1;
        service.handleSuccess(callback("JOB-1", "VJ"));
        assertEquals("PENDING_PAYMENT", store.jobs.get("job-1"));
        assertTrue(store.get("JOB-1").isEffectPending());

        assertEquals(0, service.recover(), "a payment confirmed seconds ago is left to the live callbacks");
        store.backdatePaidAt("JOB-1");
        assertEquals(1, service.recover());

        assertEquals("OPEN", store.jobs.get("job-1"));
        assertFalse(store.get("JOB-1").isEffectPending());
        assertEquals(0, service.recover(), "nothing left to recover");
        assertEquals(1, notifier.sent.size());
    }

    @Test
    void crashAfterThePlanWasExtendedDoesNotExtendItAgainOnRetry() {
        subscription("SUB-1");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        store.failAfterExtend = 1;          // plan extended, then the process "dies" before recording it

        service.handleSuccess(callback("SUB-1", "V1"));
        Instant afterCrash = store.employerExpiry.get("emp-1");
        assertTrue(store.get("SUB-1").isEffectPending());

        store.backdatePaidAt("SUB-1");
        service.recover();
        service.handleIpn(callback("SUB-1", "V1"));

        assertEquals(afterCrash, store.employerExpiry.get("emp-1"), "the retry must not add a second period");
        assertEquals(1, store.employerApplied.get("emp-1").size());
        assertFalse(store.get("SUB-1").isEffectPending());
        assertEquals(1, notifier.sent.size());
    }

    @Test
    void renewalsStillStackWhenTheyAreDifferentPayments() {
        subscription("SUB-1");
        subscription("SUB-2");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        gateway.confirms("V2", "SUB-2", "2000.00", "BDT");

        service.handleSuccess(callback("SUB-1", "V1"));
        service.handleSuccess(callback("SUB-2", "V2"));

        assertAboutOnePeriod(store.employerExpiry.get("emp-1"), 60);
        assertEquals(2, store.employerApplied.get("emp-1").size());
    }

    @Test
    void paidJobThatWasDeletedIsRecordedForManualReviewNotRetriedForever() {
        jobFee("JOB-1", "job-1");
        gateway.confirms("VJ", "JOB-1", "500.00", "BDT");
        store.jobs.remove("job-1");          // the employer deleted the job before the payment came back

        String redirect = service.handleSuccess(callback("JOB-1", "VJ"));

        Payment p = store.get("JOB-1");
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        assertFalse(p.isEffectPending(), "permanent problem: not retried");
        assertNotNull(p.getEffectError(), "but recorded, so it cannot silently look fine");
        assertTrue(redirect.contains("status=pending"), redirect);
        assertEquals(1, audit.count("PAYMENT_EFFECT_FAILED"));
        assertTrue(notifier.sent.get(0).contains("contact support"));
        store.backdatePaidAt("JOB-1");
        assertEquals(0, service.recover());
    }

    // ------------------------------------------------------------ forged / mismatched / replayed

    @Test
    void successCallbackWithoutValIdChangesNothingAndNeverCallsTheGateway() {
        subscription("SUB-1");

        String redirect = service.handleSuccess(callback("SUB-1", null));
        service.handleIpn(callback("SUB-1", "  "));

        assertEquals(PaymentStatus.PENDING, store.get("SUB-1").getStatus());
        assertEquals(0, gateway.validateCalls.get());
        assertNull(store.employerExpiry.get("emp-1"));
        assertTrue(redirect.contains("status=pending"));
    }

    @Test
    void aBrowserRedirectAloneIsNeverProof() {
        subscription("SUB-1");
        // The browser claims success with a made-up val_id; the gateway has never heard of it.
        String redirect = service.handleSuccess(callback("SUB-1", "FORGED"));

        assertEquals(PaymentStatus.PENDING, store.get("SUB-1").getStatus());
        assertNull(store.employerExpiry.get("emp-1"));
        assertTrue(redirect.contains("status=error"));
        assertEquals(0, notifier.sent.size());
    }

    @Test
    void valIdOfADifferentTransactionCannotActivateThisPayment() {
        subscription("SUB-1");
        subscription("SUB-2");
        gateway.confirms("V-OTHER", "SUB-2", "2000.00", "BDT");

        String redirect = service.handleSuccess(callback("SUB-1", "V-OTHER"));

        assertEquals(PaymentStatus.PENDING, store.get("SUB-1").getStatus());
        assertNull(store.employerExpiry.get("emp-1"));
        assertTrue(redirect.contains("status=error"));
        assertEquals(1, audit.count("PAYMENT_REJECTED"));
    }

    @Test
    void aCheaperAmountOrWrongOrMissingCurrencyIsRejected() {
        subscription("SUB-1");
        gateway.confirms("V-CHEAP", "SUB-1", "10.00", "BDT");
        gateway.confirms("V-USD", "SUB-1", "2000.00", "USD");
        gateway.confirms("V-NOCUR", "SUB-1", "2000.00", null);

        service.handleSuccess(callback("SUB-1", "V-CHEAP"));
        service.handleSuccess(callback("SUB-1", "V-USD"));
        service.handleSuccess(callback("SUB-1", "V-NOCUR"));

        assertEquals(PaymentStatus.PENDING, store.get("SUB-1").getStatus());
        assertNull(store.employerExpiry.get("emp-1"));
        assertEquals(3, audit.count("PAYMENT_REJECTED"));
    }

    @Test
    void aSuccessfulPaymentCannotBeReplayedAgainstAnotherPaymentRecord() {
        subscription("SUB-1");
        subscription("SUB-2");
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        service.handleSuccess(callback("SUB-1", "V1"));

        // Replaying V1 against SUB-2: the gateway (correctly) says V1 belongs to SUB-1 -> mismatch.
        service.handleSuccess(callback("SUB-2", "V1"));
        assertEquals(PaymentStatus.PENDING, store.get("SUB-2").getStatus());

        // Even if validation data were wrong enough to name SUB-2, the one-val_id-one-payment unique index stops it.
        gateway.confirms("V1", "SUB-2", "2000.00", "BDT");
        String redirect = service.handleSuccess(callback("SUB-2", "V1"));
        assertEquals(PaymentStatus.PENDING, store.get("SUB-2").getStatus());
        assertTrue(redirect.contains("status=error"));
        assertEquals(1, store.employerApplied.get("emp-1").size());
    }

    @Test
    void anEmployerPaymentCannotCarryAStudentIdAndViceVersa() {
        Payment p = studentPro("STU-1");
        Payment mixed = store.get("STU-1");
        mixed.setEmployerId("emp-9");           // corrupted: a student payment that also names an employer
        store.payments.put(mixed.getId(), mixed);
        gateway.confirms("VS", "STU-1", "299.00", "BDT");

        service.handleSuccess(callback("STU-1", "VS"));

        assertEquals(PaymentStatus.PENDING, store.get("STU-1").getStatus());
        assertNull(store.studentExpiry.get("stu-1"));
        assertNotNull(p);
    }

    @Test
    void unknownTransactionIsAnErrorAndTouchesNothing() {
        String redirect = service.handleSuccess(callback("NOPE", "V1"));
        assertTrue(redirect.contains("status=error"));
        assertEquals(0, gateway.validateCalls.get());
    }

    // ------------------------------------------------------------ gateway unavailable

    @Test
    void gatewayOutageDoesNotFailAPaymentThatMayHaveBeenPaid() {
        subscription("SUB-1");
        gateway.down = true;

        String redirect = service.handleSuccess(callback("SUB-1", "V1"));

        Payment p = store.get("SUB-1");
        assertEquals(PaymentStatus.PENDING, p.getStatus(), "an unanswered validation is not a failed payment");
        assertEquals("V1", p.getPendingValId());
        assertNull(store.employerExpiry.get("emp-1"));
        assertTrue(redirect.contains("status=pending"), redirect);
        assertEquals(1, audit.count("PAYMENT_VERIFY_DEFERRED"));
    }

    @Test
    void deferredValidationIsRetriedAndActivatesOnceTheGatewayIsBack() {
        subscription("SUB-1");
        gateway.down = true;
        service.handleSuccess(callback("SUB-1", "V1"));

        assertEquals(1, service.recover(), "still down: looked at, nothing changes");
        assertEquals(PaymentStatus.PENDING, store.get("SUB-1").getStatus());
        assertEquals("V1", store.get("SUB-1").getPendingValId());

        gateway.down = false;
        gateway.confirms("V1", "SUB-1", "2000.00", "BDT");
        service.recover();

        Payment p = store.get("SUB-1");
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        assertNull(p.getPendingValId());
        assertFalse(p.isEffectPending());
        assertAboutOnePeriod(store.employerExpiry.get("emp-1"), 30);
        assertEquals(1, notifier.sent.size());
        assertEquals(0, service.recover());
    }

    @Test
    void deferredValidationThatTheGatewayRejectsIsDroppedWithoutActivating() {
        subscription("SUB-1");
        gateway.down = true;
        service.handleSuccess(callback("SUB-1", "BOGUS"));
        gateway.down = false;               // gateway is back and has never heard of BOGUS

        service.recover();

        assertEquals(PaymentStatus.PENDING, store.get("SUB-1").getStatus());
        assertNull(store.get("SUB-1").getPendingValId());
        assertNull(store.employerExpiry.get("emp-1"));
        assertEquals(0, service.recover());
    }
}
