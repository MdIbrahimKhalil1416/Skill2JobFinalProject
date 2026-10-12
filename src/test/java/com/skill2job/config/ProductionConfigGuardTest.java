package com.skill2job.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** The guard that stops an unsafe production start but never interferes with local development. */
class ProductionConfigGuardTest {

    /** A real Spring Environment with no system/env property sources: only the values a test puts in, so the
     * machine running the tests can never influence the result. */
    static class FakeEnv extends AbstractEnvironment {
        final Map<String, Object> props = new HashMap<>();
        FakeEnv(String... profiles) {
            getPropertySources().addFirst(new MapPropertySource("test", props));
            setActiveProfiles(profiles);
        }
        FakeEnv with(String k, String v) { props.put(k, v); return this; }
    }

    private static FakeEnv safeProd() {
        return new FakeEnv("prod")
                .with("app.jwt.secret", "k8Jd93LmQx7VbN2pRt5YwZ0aHf6UcEi4SgTo1XyBqMn")
                .with("app.payment.base-url", "https://skill2job.onrender.com")
                .with("spring.mail.username", "me@example.org");
    }

    @Test
    void safeProductionStarts() {
        new ProductionConfigGuard(safeProd());
    }

    @Test
    void productionWithoutAJwtSecretFailsWithoutRevealingAnyValue() {
        FakeEnv env = safeProd().with("app.jwt.secret", "");
        try {
            new ProductionConfigGuard(env);
            fail("production must not start without JWT_SECRET");
        } catch (ProductionConfigException e) {
            assertEquals(1, e.getProblems().size());
            assertTrue(e.getMessage().contains("JWT_SECRET"));
        }
    }

    @Test
    void productionWithTheDevelopmentDefaultSecretFails() {
        FakeEnv env = safeProd().with("app.jwt.secret", "dev-only-secret-change-me-please-0123456789abcdef");
        try {
            new ProductionConfigGuard(env);
            fail("the public development secret must be refused in production");
        } catch (ProductionConfigException e) {
            assertTrue(e.getMessage().contains("placeholder"), e.getMessage());
            assertTrue(!e.getMessage().contains("0123456789abcdef"), "the secret value must not be echoed");
        }
    }

    @Test
    void productionFailsAndListsEveryProblemAtOnce() {
        FakeEnv env = safeProd().with("app.jwt.secret", "").with("app.otp.log-to-console", "true")
                .with("app.payment.sslcommerz.sandbox", "false");
        try {
            new ProductionConfigGuard(env);
            fail("expected a refusal");
        } catch (ProductionConfigException e) {
            assertTrue(e.getProblems().size() >= 4, e.getProblems().toString());
        }
    }

    @Test
    void developmentAndOtherProfilesAreNeverBlocked() {
        new ProductionConfigGuard(new FakeEnv("dev").with("app.jwt.secret", "dev-only-secret-change-me-please-0123456789abcdef")
                .with("app.otp.log-to-console", "true"));
        new ProductionConfigGuard(new FakeEnv());
    }
}
