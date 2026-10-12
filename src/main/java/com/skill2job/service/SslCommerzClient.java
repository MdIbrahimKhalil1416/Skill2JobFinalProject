package com.skill2job.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Thin wrapper around SSLCommerz's two REST calls (see https://developer.sslcommerz.com/doc/v4/):
 * the Session API, which opens a checkout and returns a GatewayPageURL to redirect the customer to,
 * and the Order Validation API, which PaymentService calls server-to-server after SSLCommerz
 * redirects back, so a forged or tampered browser redirect can never mark a payment as paid - only
 * a val_id that SSLCommerz itself confirms does that.
 *
 * Sandbox credentials (app.payment.sslcommerz.store-id / store-password) default to SSLCommerz's own
 * published example values ("testbox" / "qwerty"); if those stop working, register a free personal
 * sandbox store at https://developer.sslcommerz.com/registration/ and set SSLCOMMERZ_STORE_ID /
 * SSLCOMMERZ_STORE_PASSWORD instead - nothing else in the integration needs to change.
 */
@Component
public class SslCommerzClient {

    private static final Logger log = LoggerFactory.getLogger(SslCommerzClient.class);

    private final RestTemplate restTemplate;

    @Value("${app.payment.sslcommerz.store-id}")
    private String storeId;
    @Value("${app.payment.sslcommerz.store-password}")
    private String storePassword;
    @Value("${app.payment.sslcommerz.sandbox:true}")
    private boolean sandbox;

    public SslCommerzClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String sessionUrl() {
        return sandbox ? "https://sandbox.sslcommerz.com/gwprocess/v4/api.php"
                : "https://securepay.sslcommerz.com/gwprocess/v4/api.php";
    }

    private String validationUrl() {
        return sandbox ? "https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php"
                : "https://securepay.sslcommerz.com/validator/api/validationserverAPI.php";
    }

    public record SessionResult(boolean success, String gatewayUrl, String failReason) {
        public static SessionResult ok(String gatewayUrl) { return new SessionResult(true, gatewayUrl, null); }
        public static SessionResult failed(String reason) { return new SessionResult(false, null, reason); }
    }

    /** Opens a checkout session. `fields` carries everything except store_id/store_passwd, which this
     * method adds itself (total_amount, currency, tran_id, success/fail/cancel/ipn urls, customer and
     * product fields - see PaymentService for the exact set SSLCommerz requires). */
    public SessionResult createSession(Map<String, String> fields) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("store_id", storeId);
        form.add("store_passwd", storePassword);
        fields.forEach(form::add);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

        try {
            Map<String, Object> body = asStringObjectMap(restTemplate.postForObject(sessionUrl(), entity, Map.class));
            if (body == null) {
                return SessionResult.failed("The payment gateway returned an empty response.");
            }
            if (!"SUCCESS".equalsIgnoreCase(String.valueOf(body.get("status")))) {
                Object reason = body.get("failedreason");
                if (reason == null || String.valueOf(reason).isBlank()) {
                    reason = "The payment gateway declined this request.";
                }
                log.warn("SSLCommerz session creation failed: {}", com.skill2job.config.ProductionRules.redact(String.valueOf(reason)));
                return SessionResult.failed(String.valueOf(reason));
            }
            Object url = body.get("GatewayPageURL");
            if (url == null || String.valueOf(url).isBlank()) {
                return SessionResult.failed("The payment gateway did not return a checkout URL.");
            }
            return SessionResult.ok(String.valueOf(url));
        } catch (RestClientException e) {
            log.error("Could not reach SSLCommerz to open a checkout session: {}", com.skill2job.config.ProductionRules.redact(e.getMessage()));
            return SessionResult.failed("Could not reach the payment gateway. Please try again shortly.");
        }
    }

    /** What SSLCommerz's Order Validation API says about one val_id. {@code unavailable} means we could NOT get
     * an answer (timeout, 5xx, empty or unreadable body): that is not a "no" and must never be treated as one -
     * the customer may well have paid, so the payment stays open for a later retry. {@code valid=false} with
     * {@code unavailable=false} is a real answer from the gateway: this val_id is not a paid transaction. */
    public record Validation(boolean valid, boolean unavailable, String tranId, String amount, String currency, String cardType) {
        public static Validation invalid() { return new Validation(false, false, null, null, null, null); }
        public static Validation gatewayDown() { return new Validation(false, true, null, null, null, null); }
        public static Validation paid(String tranId, String amount, String currency, String cardType) {
            return new Validation(true, false, tranId, amount, currency, cardType);
        }
    }

    /** Server-to-server confirmation that val_id (received on the success/ipn callback) is a real,
     * paid SSLCommerz transaction. Never trust the browser redirect alone - this call is what actually
     * proves the payment happened. The caller must still check that tranId/amount/currency in the
     * result match its own Payment record, so a val_id from a different (e.g. cheaper) transaction can
     * never be replayed against this one. */
    public Validation validate(String valId) {
        if (valId == null || valId.isBlank()) {
            return Validation.invalid();
        }
        // A java.net.URI (not a String) so RestTemplate does not treat it as a template and encode it
        // a second time - a store password containing '%' or '@' would otherwise break validation.
        URI url = UriComponentsBuilder.fromUriString(validationUrl())
                .queryParam("val_id", valId)
                .queryParam("store_id", storeId)
                .queryParam("store_passwd", storePassword)
                .queryParam("format", "json")
                .encode()
                .build()
                .toUri();
        try {
            Map<String, Object> body = asStringObjectMap(restTemplate.getForObject(url, Map.class));
            if (body == null) {
                log.error("SSLCommerz returned an empty validation response for val_id {}", valId);
                return Validation.gatewayDown();
            }
            String status = String.valueOf(body.get("status"));
            boolean valid = "VALID".equalsIgnoreCase(status) || "VALIDATED".equalsIgnoreCase(status);
            if (!valid) {
                return Validation.invalid();
            }
            // The Validation API reports the currency as currency_type; "currency" is accepted too.
            String currency = body.get("currency_type") != null ? str(body.get("currency_type")) : str(body.get("currency"));
            return Validation.paid(str(body.get("tran_id")), str(body.get("amount")), currency, str(body.get("card_type")));
        } catch (RestClientException | IllegalArgumentException e) {
            // RestTemplate messages contain the full request URL, which carries store_passwd: redact before logging.
            log.error("Could not validate val_id {} with SSLCommerz: {}", valId, com.skill2job.config.ProductionRules.redact(e.getMessage()));
            return Validation.gatewayDown();
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** RestTemplate hands back a raw Map for a JSON object; this narrows it in one place so the rest of
     * the class can use ordinary typed calls (no Map<?, ?> wildcard - its getOrDefault() cannot take a
     * literal default value, which was a compile error). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Map<?, ?> raw) {
        return (Map<String, Object>) raw;
    }
}
