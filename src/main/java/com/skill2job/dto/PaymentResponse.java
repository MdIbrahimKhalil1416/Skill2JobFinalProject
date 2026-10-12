package com.skill2job.dto;

import com.skill2job.entity.Payment;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.PaymentStatus;
import com.skill2job.enums.Role;
import com.skill2job.enums.SubscriptionPlan;

import java.time.Instant;

public record PaymentResponse(String id, String tranId, PaymentPurpose purpose, String jobId, SubscriptionPlan plan,
                              double amount, String currency, PaymentStatus status, String paymentMethod,
                              Instant createdAt, Instant paidAt, Role payerRole) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getTranId(), p.getPurpose(), p.getJobId(), p.getPlan(),
                p.getAmount(), p.getCurrency(), p.getStatus(), p.getPaymentMethod(), p.getCreatedAt(), p.getPaidAt(), p.payerRole());
    }
}
