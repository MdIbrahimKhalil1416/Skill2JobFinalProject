package com.skill2job.scheduler;

import com.skill2job.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Safety net for payments: finishes the activation of payments that SSLCommerz confirmed but whose job/plan could
 * not be applied (database hiccup, restart mid-way), and re-validates payments whose val_id arrived while the
 * gateway was unreachable. Idempotent, so it is harmless on several instances or alongside live callbacks.
 */
@Component
public class PaymentRecoveryScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentRecoveryScheduler.class);

    private final PaymentService paymentService;

    public PaymentRecoveryScheduler(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${app.payment.recovery-interval-ms:300000}", initialDelayString = "${app.payment.recovery-initial-delay-ms:60000}")
    public void run() {
        try {
            int n = paymentService.recover();
            if (n > 0) {
                log.info("Payment recovery looked at {} payment(s)", n);
            }
        } catch (RuntimeException e) {
            log.error("Payment recovery run failed: {}", e.getMessage());
        }
    }
}
