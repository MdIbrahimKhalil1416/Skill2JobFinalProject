package com.skill2job.controller;

import com.skill2job.dto.PaymentInitResponse;
import com.skill2job.dto.PaymentResponse;
import com.skill2job.dto.StudentPremiumResponse;
import com.skill2job.enums.SubscriptionPlan;
import com.skill2job.security.AuthUser;
import com.skill2job.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Job-posting fee + employer Pro subscription + Student Pro payments via SSLCommerz.
 *
 * The /sslcommerz/* endpoints are called directly by SSLCommerz (success_url/fail_url/cancel_url as a
 * browser redirect, ipn_url as a server-to-server call) - never by our own frontend - so they take no
 * JWT and must stay permitAll in SecurityConfig. They are form-urlencoded POSTs per SSLCommerz's docs.
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/job/{jobId}/initiate")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Start a payment to publish a job that is pending payment")
    public PaymentInitResponse initiateJobPayment(@AuthenticationPrincipal AuthUser user, @PathVariable String jobId) {
        return paymentService.initiateJobPayment(user, jobId);
    }

    @PostMapping("/subscription/initiate")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Start a payment to buy/renew the Pro subscription")
    public PaymentInitResponse initiateSubscriptionPayment(@AuthenticationPrincipal AuthUser user,
                                                            @RequestParam(defaultValue = "PRO") SubscriptionPlan plan) {
        return paymentService.initiateSubscriptionPayment(user, plan);
    }

    @PostMapping("/student/pro/initiate")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Start a payment to buy/renew Student Pro")
    public PaymentInitResponse initiateStudentPro(@AuthenticationPrincipal AuthUser user) {
        return paymentService.initiateStudentProPayment(user);
    }

    @GetMapping("/student/premium")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Student Pro price, duration and whether it is active")
    public StudentPremiumResponse studentPremium(@AuthenticationPrincipal AuthUser user) {
        return paymentService.studentPremium(user);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLOYER','STUDENT')")
    @Operation(summary = "My billing history (employers see their payments, students see theirs)")
    public List<PaymentResponse> myPayments(@AuthenticationPrincipal AuthUser user) {
        return paymentService.myPayments(user);
    }

    // ---------------------------------------------------------------- SSLCommerz callbacks (public)

    @RequestMapping(value = "/sslcommerz/success", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "SSLCommerz success redirect (called by the gateway, not the frontend)")
    public ResponseEntity<Void> success(@RequestParam Map<String, String> params) {
        return redirect(paymentService.handleSuccess(params));
    }

    @RequestMapping(value = "/sslcommerz/fail", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "SSLCommerz failure redirect (called by the gateway, not the frontend)")
    public ResponseEntity<Void> fail(@RequestParam Map<String, String> params) {
        return redirect(paymentService.handleFail(params));
    }

    @RequestMapping(value = "/sslcommerz/cancel", method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "SSLCommerz cancellation redirect (called by the gateway, not the frontend)")
    public ResponseEntity<Void> cancel(@RequestParam Map<String, String> params) {
        return redirect(paymentService.handleCancel(params));
    }

    @PostMapping("/sslcommerz/ipn")
    @Operation(summary = "SSLCommerz server-to-server IPN (reliability backup; needs a public base URL)")
    public ResponseEntity<String> ipn(@RequestParam Map<String, String> params) {
        paymentService.handleIpn(params);
        return ResponseEntity.ok("OK");
    }

    private ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }
}
