package com.skill2job.dto;

/** Returned when a payment checkout session is opened: the frontend does a full-page redirect
 * (window.location.href = gatewayUrl) to SSLCommerz's hosted checkout page. */
public record PaymentInitResponse(String gatewayUrl, String tranId, double amount, String currency) {
}
