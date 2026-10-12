package com.skill2job.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sends one plain-text email through Brevo's transactional email REST API (v3, HTTPS on port 443).
 *
 * Why: Render's free web services block outbound SMTP ports (25/465/587), so Gmail SMTP cannot work there,
 * but an HTTPS API call can. Uses only the JDK HttpClient (no new dependency) with connect and request timeouts.
 *
 * Never logged: the API key, the email body (which can contain an OTP or reset code). On failure only the HTTP
 * status and Brevo's short error code/message are logged, and {@code false} is returned so the caller can show
 * the user a clear "could not send the email" message instead of an error page.
 */
final class BrevoEmailClient {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailClient.class);

    static final String DEFAULT_URL = "https://api.brevo.com/v3/smtp/email";
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /** The HTTP call, separated so tests can run without any network. */
    interface Transport {
        Response post(URI uri, Map<String, String> headers, String jsonBody, Duration timeout)
                throws IOException, InterruptedException;
    }

    record Response(int status, String body) { }

    private final String apiKey;
    private final URI uri;
    private final Transport transport;

    BrevoEmailClient(String apiKey, String url, Transport transport) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.uri = URI.create(url == null || url.isBlank() ? DEFAULT_URL : url.trim());
        this.transport = transport == null ? jdkTransport() : transport;
    }

    boolean configured() {
        return !apiKey.isEmpty();
    }

    /** @return true when Brevo accepted the email (HTTP 2xx). */
    boolean send(String fromEmail, String fromName, String toEmail, String subject, String text) {
        if (!configured()) {
            return false;
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("api-key", apiKey);
        headers.put("accept", "application/json");
        headers.put("content-type", "application/json");
        String json = json(fromEmail, fromName, toEmail, subject, text);
        try {
            Response r = transport.post(uri, headers, json, REQUEST_TIMEOUT);
            if (r.status() >= 200 && r.status() < 300) {
                return true;
            }
            log.error("Brevo refused the email to {} (\"{}\"): HTTP {} {}", toEmail, subject, r.status(), errorSummary(r.body()));
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sending the email to {} (\"{}\") through Brevo was interrupted.", toEmail, subject);
            return false;
        } catch (IOException | RuntimeException e) {
            log.error("Could not reach Brevo to send the email to {} (\"{}\"): {}", toEmail, subject, e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return false;
        }
    }

    /** The request body Brevo expects. Built by hand (no JSON library needed for five fields). */
    static String json(String fromEmail, String fromName, String toEmail, String subject, String text) {
        return "{\"sender\":{\"name\":" + quote(fromName == null || fromName.isBlank() ? "Skill2Job" : fromName)
                + ",\"email\":" + quote(fromEmail) + "},"
                + "\"to\":[{\"email\":" + quote(toEmail) + "}],"
                + "\"subject\":" + quote(subject) + ","
                + "\"textContent\":" + quote(text) + "}";
    }

    static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    private static final Pattern FIELD = Pattern.compile("\"(code|message)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.){0,200})");

    /** Only Brevo's short "code" and "message" fields, e.g. unauthorized / "Key not found". Never our request. */
    static String errorSummary(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        Matcher m = FIELD.matcher(body);
        while (m.find()) {
            out.append(out.length() == 0 ? "" : ", ").append(m.group(1)).append('=').append(m.group(2));
        }
        return out.length() == 0 ? "" : "(" + out + ")";
    }

    private static Transport jdkTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        return (uri, headers, body, timeout) -> {
            HttpRequest.Builder req = HttpRequest.newBuilder(uri).timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(req::header);
            HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(res.statusCode(), res.body());
        };
    }
}
