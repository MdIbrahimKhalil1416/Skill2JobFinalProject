package com.skill2job.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Brevo HTTPS email: request shape, automatic provider choice, failures that never throw. No network is used. */
class BrevoEmailTest {

    record Call(URI uri, Map<String, String> headers, String body, Duration timeout) { }

    static final class FakeTransport implements BrevoEmailClient.Transport {
        final List<Call> calls = new ArrayList<>();
        int status = 201;
        String responseBody = "{\"messageId\":\"<x@smtp-relay>\"}";
        IOException fail;

        @Override
        public BrevoEmailClient.Response post(URI uri, Map<String, String> headers, String jsonBody, Duration timeout)
                throws IOException {
            calls.add(new Call(uri, headers, jsonBody, timeout));
            if (fail != null) throw fail;
            return new BrevoEmailClient.Response(status, responseBody);
        }
    }

    private static EmailService service(String brevoKey, String smtpUser, FakeTransport t) {
        EmailService s = new EmailService(null);
        OtpServiceTest.set(s, "brevoApiKey", brevoKey);
        OtpServiceTest.set(s, "brevoUrl", BrevoEmailClient.DEFAULT_URL);
        OtpServiceTest.set(s, "fromAddress", "sender@gmail.com");
        OtpServiceTest.set(s, "fromName", "Skill2Job");
        OtpServiceTest.set(s, "mailUsername", smtpUser);
        OtpServiceTest.set(s, "logOtpToConsole", false);
        if (t != null) s.useBrevoTransport(t);
        return s;
    }

    @Test
    void withAnApiKeyEveryEmailGoesThroughBrevoOverHttps() {
        FakeTransport t = new FakeTransport();
        EmailService s = service("xkeysib-test", "", t);
        assertTrue(s.sendOtpEmail("user@example.com", "Rahim", "123456", 5));
        assertTrue(s.sendPasswordResetEmail("user@example.com", "Rahim", "654321", 15));
        assertEquals(2, t.calls.size());
        Call c = t.calls.get(0);
        assertEquals("https://api.brevo.com/v3/smtp/email", c.uri().toString());
        assertEquals("xkeysib-test", c.headers().get("api-key"));
        assertEquals("application/json", c.headers().get("content-type"));
        assertTrue(c.body().contains("\"sender\":{\"name\":\"Skill2Job\",\"email\":\"sender@gmail.com\"}"), c.body());
        assertTrue(c.body().contains("\"to\":[{\"email\":\"user@example.com\"}]"));
        assertTrue(c.body().contains("123456"), "the code is in the email itself");
        assertEquals(BrevoEmailClient.REQUEST_TIMEOUT, c.timeout(), "every call has a timeout");
    }

    @Test
    void theBrevoKeyAloneIsEnoughNoSmtpLoginNeeded() {
        EmailService s = service("xkeysib-test", "", new FakeTransport());
        assertTrue(s.emailConfigured());
        assertFalse(s.devConsoleFallback());
    }

    @Test
    void withoutAKeyBrevoIsNeverCalled() {
        FakeTransport t = new FakeTransport();
        EmailService s = service("", "", t);
        assertFalse(s.sendOtpEmail("user@example.com", "Rahim", "123456", 5), "nothing configured: not delivered");
        assertTrue(t.calls.isEmpty());
        assertFalse(s.emailConfigured());
    }

    @Test
    void aRejectedOrUnreachableBrevoReturnsFalseAndNeverThrows() {
        FakeTransport t = new FakeTransport();
        t.status = 401;
        t.responseBody = "{\"code\":\"unauthorized\",\"message\":\"Key not found\"}";
        EmailService s = service("bad-key", "", t);
        assertFalse(s.sendOtpEmail("user@example.com", "Rahim", "123456", 5));

        t.fail = new IOException("connect timed out");
        assertFalse(s.sendPasswordChangedEmail("user@example.com", "Rahim"));
    }

    @Test
    void textIsSafelyEscapedIntoJson() {
        String json = BrevoEmailClient.json("a@b.c", "Skill \"2\" Job", "x@y.z", "Line\\one", "Hi\n\t\"there\"\u0001");
        assertTrue(json.contains("\"name\":\"Skill \\\"2\\\" Job\""), json);
        assertTrue(json.contains("\"subject\":\"Line\\\\one\""), json);
        assertTrue(json.contains("\"textContent\":\"Hi\\n\\t\\\"there\\\"\\u0001\""), json);
    }

    @Test
    void errorSummaryKeepsOnlyBrevosShortFields() {
        assertEquals("(code=unauthorized, message=Key not found)",
                BrevoEmailClient.errorSummary("{\"code\":\"unauthorized\",\"message\":\"Key not found\"}"));
        assertEquals("", BrevoEmailClient.errorSummary("<html>gateway error</html>"));
        assertEquals("", BrevoEmailClient.errorSummary(null));
    }
}
