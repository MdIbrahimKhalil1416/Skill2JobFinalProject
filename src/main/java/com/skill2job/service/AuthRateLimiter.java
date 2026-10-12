package com.skill2job.service;

import com.skill2job.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Small in-memory sliding-window limiter for the public auth endpoints (register, login, OTP, resend, forgot,
 * reset). It stops one client from using the app to mail-bomb an address, create endless unverified accounts or
 * brute-force a password. It is per application instance: fine for a single Render instance; with several
 * instances each keeps its own count (a shared store would be needed then).
 *
 * Per-email limits do the real protection (they bound what a victim's inbox receives). Per-IP limits are
 * deliberately generous because a campus or office shares one address.
 */
@Component
public class AuthRateLimiter {

    private static final int MAX_KEYS = 20_000;

    private final Map<String, ArrayDeque<Long>> hits = new ConcurrentHashMap<>();
    private LongSupplier clock = System::currentTimeMillis;

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled = true;

    @Value("${app.security.client-ip-hops:1}")
    private int clientIpHops = 1;

    /** Test hook. */
    void configure(boolean enabled, LongSupplier clock, int hops) {
        this.enabled = enabled;
        this.clock = clock;
        this.clientIpHops = hops;
    }

    /** Counts one request for {@code key} and throws 429 when it is over {@code max} within {@code window}. */
    public void check(String bucket, String key, int max, Duration window) {
        if (!enabled) {
            return;
        }
        String k = bucket + "|" + (key == null ? "" : key.trim().toLowerCase(Locale.ROOT));
        long now = clock.getAsLong();
        long windowMs = window.toMillis();
        if (hits.size() > MAX_KEYS) {
            hits.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    return e.getValue().isEmpty() || now - e.getValue().peekLast() > windowMs * 2;
                }
            });
        }
        ArrayDeque<Long> q = hits.computeIfAbsent(k, x -> new ArrayDeque<>());
        long retryAfterSec = 0;
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() >= windowMs) {
                q.pollFirst();
            }
            if (q.size() >= max) {
                retryAfterSec = Math.max(1, (windowMs - (now - q.peekFirst()) + 999) / 1000);
            } else {
                q.addLast(now);
            }
        }
        if (retryAfterSec > 0) {
            throw ApiException.tooManyRequests("Too many requests. Please try again in "
                    + (retryAfterSec >= 120 ? (retryAfterSec / 60) + " minute(s)" : retryAfterSec + " second(s)") + ".");
        }
    }

    /** The caller's address. Behind a proxy (Render) the real client is the entry the proxy appended to
     * X-Forwarded-For, i.e. the last one for one hop; anything a client puts in front of it is ignored. */
    public String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && clientIpHops > 0) {
            String[] parts = xff.split(",");
            int idx = parts.length - clientIpHops;
            String ip = parts[Math.max(0, idx)].trim();
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
