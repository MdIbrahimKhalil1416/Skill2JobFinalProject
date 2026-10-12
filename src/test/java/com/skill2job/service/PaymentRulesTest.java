package com.skill2job.service;

import com.skill2job.entity.Payment;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentRulesTest {

    private static Payment studentPayment() {
        Payment p = new Payment();
        p.setStudentId("stu-1");
        p.setPayerRoleValue(Role.STUDENT);
        p.setPurpose(PaymentPurpose.STUDENT_PRO);
        p.setTranId("STU-stu-1-1");
        p.setAmount(299);
        return p;
    }

    private static Payment employerJobPayment() {
        Payment p = new Payment();
        p.setEmployerId("emp-1");
        p.setPayerRoleValue(Role.EMPLOYER);
        p.setPurpose(PaymentPurpose.JOB_POST);
        p.setJobId("job-1");
        p.setTranId("JOB-emp-1-1");
        p.setAmount(500);
        return p;
    }

    // ---------------------------------------------------------------- gateway validation match

    @Test
    void matchingValidationIsAccepted() {
        assertNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "299.00", "BDT"));
        assertNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "299.0000", "bdt"));
    }

    @Test
    void valIdFromAnotherTransactionIsRejected() {
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-other-9", "299.00", "BDT"));
        assertNotNull(PaymentRules.mismatch(studentPayment(), null, "299.00", "BDT"));
    }

    @Test
    void cheaperOrDifferentAmountIsRejected() {
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "10.00", "BDT"));
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "299.50", "BDT"));
        assertNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "299.005", "BDT"), "sub-paisa rounding is tolerated");
    }

    @Test
    void aValidationWithoutACurrencyIsRejected() {
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "299.00", null));
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "299.00", "  "));
    }

    @Test
    void wrongCurrencyIsRejected() {
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "299.00", "USD"));
    }

    @Test
    void unreadableAmountIsRejected() {
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", null, "BDT"));
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "abc", "BDT"));
        assertNotNull(PaymentRules.mismatch(studentPayment(), "STU-stu-1-1", "", "BDT"));
    }

    // ---------------------------------------------------------------- employer vs student separation

    @Test
    void purposesBelongToExactlyOneRole() {
        assertEquals(Role.EMPLOYER, PaymentPurpose.JOB_POST.payerRole());
        assertEquals(Role.EMPLOYER, PaymentPurpose.SUBSCRIPTION.payerRole());
        assertEquals(Role.STUDENT, PaymentPurpose.STUDENT_PRO.payerRole());
    }

    @Test
    void consistentPaymentsPass() {
        assertNull(PaymentRules.inconsistency(studentPayment()));
        assertNull(PaymentRules.inconsistency(employerJobPayment()));
    }

    @Test
    void studentPurposeCannotBePaidByAnEmployerAccount() {
        Payment p = studentPayment();
        p.setPayerRoleValue(Role.EMPLOYER);
        p.setEmployerId("emp-1");
        assertNotNull(PaymentRules.inconsistency(p));
    }

    @Test
    void employerPurposeCannotBePaidByAStudentAccount() {
        Payment p = employerJobPayment();
        p.setPayerRoleValue(Role.STUDENT);
        p.setStudentId("stu-1");
        p.setEmployerId(null);
        assertNotNull(PaymentRules.inconsistency(p));
    }

    @Test
    void studentPaymentMustNotCarryAnEmployerId() {
        Payment p = studentPayment();
        p.setEmployerId("emp-1");
        assertNotNull(PaymentRules.inconsistency(p));
    }

    @Test
    void studentPaymentNeedsItsStudentId() {
        Payment p = studentPayment();
        p.setStudentId(null);
        assertNotNull(PaymentRules.inconsistency(p));
        p.setStudentId(" ");
        assertNotNull(PaymentRules.inconsistency(p));
    }

    @Test
    void jobPostPaymentNeedsAJob() {
        Payment p = employerJobPayment();
        p.setJobId(null);
        assertNotNull(PaymentRules.inconsistency(p));
    }

    @Test
    void paymentsWithoutPurposeAreRejected() {
        assertNotNull(PaymentRules.inconsistency(new Payment()));
    }

    @Test
    void legacyEmployerPaymentWithoutStoredRoleStillCountsAsEmployer() {
        Payment p = employerJobPayment();
        p.setPayerRoleValue(null);               // documents written before student payments existed
        assertEquals(Role.EMPLOYER, p.payerRole());
        assertEquals("emp-1", p.ownerId());
        assertNull(PaymentRules.inconsistency(p));
    }

    @Test
    void ownerIdFollowsTheRole() {
        assertEquals("stu-1", studentPayment().ownerId());
        assertEquals("emp-1", employerJobPayment().ownerId());
    }

    // ---------------------------------------------------------------- status transitions

    @Test
    void successIsFinalButFailedAndCancelledCanStillBePaid() {
        assertTrue(PaymentRules.canBecomeSuccess(PaymentStatus.PENDING));
        assertTrue(PaymentRules.canBecomeSuccess(PaymentStatus.FAILED));
        assertTrue(PaymentRules.canBecomeSuccess(PaymentStatus.CANCELLED));
        assertFalse(PaymentRules.canBecomeSuccess(PaymentStatus.SUCCESS), "a second callback must not re-apply a payment");
        assertFalse(PaymentRules.canBecomeSuccess(null));
    }

    @Test
    void failOrCancelCallbacksOnlyMoveAPendingPayment() {
        assertTrue(PaymentRules.canBecomeFailedOrCancelled(PaymentStatus.PENDING));
        assertFalse(PaymentRules.canBecomeFailedOrCancelled(PaymentStatus.SUCCESS), "a late fail callback must never undo a paid payment");
        assertFalse(PaymentRules.canBecomeFailedOrCancelled(PaymentStatus.FAILED));
        assertFalse(PaymentRules.canBecomeFailedOrCancelled(PaymentStatus.CANCELLED));
    }

    // ---------------------------------------------------------------- expiry maths

    @Test
    void firstPurchaseStartsFromNow() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), PaymentRules.extendFrom(null, now, 30));
    }

    @Test
    void renewalWhileActiveKeepsTheRemainingDays() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        Instant current = Instant.parse("2026-10-11T00:00:00Z");   // 10 days left
        assertEquals(Instant.parse("2026-11-10T00:00:00Z"), PaymentRules.extendFrom(current, now, 30));
    }

    @Test
    void renewalAfterExpiryStartsFromNowNotFromThePast() {
        Instant now = Instant.parse("2026-10-01T00:00:00Z");
        Instant expired = Instant.parse("2026-08-01T00:00:00Z");
        assertEquals(Instant.parse("2026-10-31T00:00:00Z"), PaymentRules.extendFrom(expired, now, 30));
    }
}
