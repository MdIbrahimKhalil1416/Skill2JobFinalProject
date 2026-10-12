package com.skill2job.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Refuses to start the "prod" profile with an unsafe configuration (missing/weak JWT secret, OTP codes in the logs,
 * live payments on demo credentials ...) and logs warnings for risky-but-allowed settings. Does nothing outside
 * production, so local development stays convenient. JwtUtil and DataSeeder depend on this bean, so it runs first.
 * It never logs a secret value - only the NAME of the setting that is wrong.
 */
@Component("productionConfigGuard")
public class ProductionConfigGuard {

    private static final Logger log = LoggerFactory.getLogger(ProductionConfigGuard.class);

    public ProductionConfigGuard(Environment env) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        Map<String, String> values = new HashMap<>();
        for (String key : ProductionRules.KEYS) {
            values.put(key, env.getProperty(key, ""));
        }
        for (String warning : ProductionRules.warnings(values)) {
            log.warn("[production config] {}", warning);
        }
        List<String> errors = ProductionRules.errors(values);
        if (!errors.isEmpty()) {
            throw new ProductionConfigException(errors);
        }
        log.info("Production configuration checks passed.");
    }
}
