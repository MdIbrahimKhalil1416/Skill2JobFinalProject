package com.skill2job.service;

import com.skill2job.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {

    private static HttpServletRequest request(String xff, String remote) {
        return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
                    case "getHeader" -> "X-Forwarded-For".equals(a[0]) ? xff : null;
                    case "getRemoteAddr" -> remote;
                    default -> null;
                });
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void blocksAfterTheLimitAndRecoversWhenTheWindowPasses() {
        AtomicLong now = new AtomicLong(1_000_000);
        AuthRateLimiter l = new AuthRateLimiter();
        l.configure(true, now::get, 1);
        Duration w = Duration.ofMinutes(10);
        for (int i = 0; i < 5; i++) assertEquals(200, status(() -> l.check("resend", "a@x.com", 5, w)));
        assertEquals(429, status(() -> l.check("resend", "a@x.com", 5, w)));
        now.addAndGet(9 * 60_000);
        assertEquals(429, status(() -> l.check("resend", "a@x.com", 5, w)), "still inside the window");
        now.addAndGet(61_000);
        assertEquals(200, status(() -> l.check("resend", "a@x.com", 5, w)));
    }

    @Test
    void keysAndBucketsAreIndependentAndEmailIsCaseInsensitive() {
        AuthRateLimiter l = new AuthRateLimiter();
        l.configure(true, System::currentTimeMillis, 1);
        Duration w = Duration.ofHours(1);
        assertEquals(200, status(() -> l.check("b", "a@x.com", 1, w)));
        assertEquals(429, status(() -> l.check("b", " A@X.com ", 1, w)), "same address, different spelling");
        assertEquals(200, status(() -> l.check("b", "other@x.com", 1, w)));
        assertEquals(200, status(() -> l.check("c", "a@x.com", 1, w)));
    }

    @Test
    void rejectedRequestsDoNotExtendTheBlock() {
        AtomicLong now = new AtomicLong(0);
        AuthRateLimiter l = new AuthRateLimiter();
        l.configure(true, now::get, 1);
        Duration w = Duration.ofSeconds(100);
        assertEquals(200, status(() -> l.check("b", "k", 1, w)));
        for (int i = 0; i < 50; i++) { now.addAndGet(1000); assertEquals(429, status(() -> l.check("b", "k", 1, w))); }
        now.addAndGet(51_000);
        assertEquals(200, status(() -> l.check("b", "k", 1, w)), "hammering while blocked must not keep it blocked");
    }

    @Test
    void canBeSwitchedOff() {
        AuthRateLimiter l = new AuthRateLimiter();
        l.configure(false, System::currentTimeMillis, 1);
        for (int i = 0; i < 100; i++) assertEquals(200, status(() -> l.check("b", "k", 1, Duration.ofHours(1))));
    }

    @Test
    void clientIpIgnoresAddressesAClientInjectsInFrontOfTheProxyEntry() {
        AuthRateLimiter l = new AuthRateLimiter();
        l.configure(true, System::currentTimeMillis, 1);
        assertEquals("203.0.113.9", l.clientIp(request("6.6.6.6, 203.0.113.9", "10.0.0.1")), "last entry = appended by our proxy");
        assertEquals("203.0.113.9", l.clientIp(request("203.0.113.9", "10.0.0.1")));
        assertEquals("10.0.0.1", l.clientIp(request(null, "10.0.0.1")));
        l.configure(true, System::currentTimeMillis, 0);
        assertEquals("10.0.0.1", l.clientIp(request("6.6.6.6", "10.0.0.1")), "hops=0: header ignored");
    }
}
