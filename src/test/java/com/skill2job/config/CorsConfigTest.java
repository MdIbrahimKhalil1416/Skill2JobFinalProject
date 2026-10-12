package com.skill2job.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: the SSLCommerz sandbox "Success" button failed with "Invalid CORS request" because the gateway's
 * cross-origin form POST to /api/payments/sslcommerz/success fell under the strict /api/** CORS rule.
 */
class CorsConfigTest {

    @Test
    void everyGatewayCallbackIsExemptFromCors() {
        for (String cb : List.of("success", "fail", "cancel", "ipn")) {
            assertTrue(CorsConfig.isGatewayCallback("/api/payments/sslcommerz/" + cb, ""), cb);
        }
        assertTrue(CorsConfig.isGatewayCallback("/app/api/payments/sslcommerz/success", "/app"), "with a context path");
    }

    @Test
    void theRestOfTheApiKeepsItsCorsRule() {
        assertFalse(CorsConfig.isGatewayCallback("/api/payments/me", ""));
        assertFalse(CorsConfig.isGatewayCallback("/api/payments/job/1/initiate", ""), "initiating a payment needs a JWT and stays strict");
        assertFalse(CorsConfig.isGatewayCallback("/api/auth/login", ""));
        assertFalse(CorsConfig.isGatewayCallback("/api/payments/sslcommerz", ""), "only the callback paths below it");
        assertFalse(CorsConfig.isGatewayCallback("/api/payments/sslcommerz/../../auth/login", ""), "no path tricks");
        assertFalse(CorsConfig.isGatewayCallback(null, ""));
    }

    @Test
    void theAppsOwnPublicOriginIsAlwaysAllowedButNothingIsOpenedToEveryone() {
        List<String> o = CorsConfig.allowedOrigins("http://localhost:8080, http://localhost:3000", "https://skill2job.onrender.com/");
        assertEquals(List.of("http://localhost:8080", "http://localhost:3000", "https://skill2job.onrender.com"), o);
        assertFalse(o.contains("*"));
    }

    @Test
    void originsAreDeduplicatedAndInvalidBaseUrlsIgnored() {
        assertEquals(List.of("http://localhost:8080"), CorsConfig.allowedOrigins("http://localhost:8080/", "http://localhost:8080"));
        assertEquals(List.of("https://a.com"), CorsConfig.allowedOrigins("https://a.com", ""));
        assertEquals(List.of("https://a.com"), CorsConfig.allowedOrigins("https://a.com", "not a url"));
        assertEquals(List.of("https://a.com"), CorsConfig.allowedOrigins(" , https://a.com ,", null));
    }

    @Test
    void originOfKeepsSchemeHostAndPortOnly() {
        assertEquals("https://x.onrender.com", CorsConfig.originOf("https://X.onrender.com/path?q=1"));
        assertEquals("http://localhost:9090", CorsConfig.originOf("http://localhost:9090"));
        assertNull(CorsConfig.originOf("ftp://x.com"));
        assertNull(CorsConfig.originOf("   "));
    }
}
