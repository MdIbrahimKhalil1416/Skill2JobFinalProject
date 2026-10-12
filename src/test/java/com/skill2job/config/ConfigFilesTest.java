package com.skill2job.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the ACTUAL configuration files (not a copy of their content) against unsafe production defaults coming back. */
class ConfigFilesTest {

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static Properties props(String path) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(read(path)));
        return p;
    }

    /** Value of a simple "${NAME:default}" placeholder when NAME is not set in the environment. */
    private static String defaultOf(String placeholder) {
        assertNotNull(placeholder);
        if (!placeholder.startsWith("${")) {
            return placeholder;
        }
        int colon = placeholder.indexOf(':');
        return colon < 0 ? "" : placeholder.substring(colon + 1, placeholder.length() - 1);
    }

    private static final String BASE = "src/main/resources/application.properties";
    private static final String DEV = "src/main/resources/application-dev.properties";
    private static final String PROD = "src/main/resources/application-prod.properties";

    @Test
    void theBaseConfigurationContainsNoSecretOrAdminPasswordFallback() throws Exception {
        Properties base = props(BASE);
        assertEquals("", defaultOf(base.getProperty("app.jwt.secret")), "production must not inherit a JWT secret");
        assertEquals("", defaultOf(base.getProperty("app.admin.password")), "production must not inherit an admin password");
        assertEquals("", defaultOf(base.getProperty("app.admin.email")));
        String text = read(BASE);
        assertFalse(text.contains("Admin@123"));
        assertFalse(text.contains("dev-only"));
    }

    @Test
    void demoDataAndOtpLoggingAreOffInTheBaseAndProductionConfiguration() throws Exception {
        for (String file : new String[]{BASE, PROD}) {
            Properties p = props(file);
            if (p.getProperty("app.seed.enabled") != null) {
                assertEquals("false", defaultOf(p.getProperty("app.seed.enabled")), file);
            }
        }
        assertEquals("false", defaultOf(props(BASE).getProperty("app.seed.enabled")));
        assertEquals("false", defaultOf(props(BASE).getProperty("app.seed.allow-in-prod")));
        assertEquals("false", defaultOf(props(BASE).getProperty("app.otp.log-to-console")));
        assertEquals("false", props(PROD).getProperty("app.otp.log-to-console"));
        assertEquals("false", props(PROD).getProperty("app.seed.print-credentials"));
    }

    @Test
    void whatTheBaseConfigurationResolvesToWithoutAnyEnvironmentIsRefusedByTheProductionRules() throws Exception {
        Properties base = props(BASE);
        assertNotNull(ProductionRules.jwtSecretProblem(defaultOf(base.getProperty("app.jwt.secret"))));
        assertNotNull(ProductionRules.adminPasswordProblem(defaultOf(base.getProperty("app.admin.password"))));
    }

    @Test
    void developmentKeepsItsConveniencesWhichProductionRulesWouldRefuse() throws Exception {
        Properties dev = props(DEV);
        String secret = defaultOf(dev.getProperty("app.jwt.secret"));
        assertTrue(secret.length() >= 32, "local run must still start without any setup");
        assertNotNull(ProductionRules.jwtSecretProblem(secret), "...but that secret is not acceptable in production");
        assertEquals("admin@skill2job.com", defaultOf(dev.getProperty("app.admin.email")));
        assertEquals("true", defaultOf(dev.getProperty("app.seed.enabled")));
        assertEquals("true", defaultOf(dev.getProperty("app.otp.log-to-console")));
    }

    @Test
    void paymentsStayInSandboxByDefault() throws Exception {
        assertEquals("true", defaultOf(props(BASE).getProperty("app.payment.sslcommerz.sandbox")));
        assertNull(props(PROD).getProperty("app.payment.sslcommerz.sandbox"), "prod must not flip the mode on its own");
    }

    @Test
    void renderBlueprintIsProductionSafeAndHoldsNoSecrets() throws Exception {
        String y = read("render.yaml");
        assertTrue(y.contains("key: SPRING_PROFILES_ACTIVE\n        value: prod"));
        assertTrue(y.contains("key: JWT_SECRET\n        generateValue: true"));
        assertTrue(y.contains("key: ADMIN_PASSWORD\n        sync: false"));
        assertTrue(y.contains("key: ADMIN_EMAIL\n        sync: false"));
        assertTrue(y.contains("key: APP_BASE_URL\n        sync: false"));
        assertTrue(y.contains("key: MAIL_PASSWORD\n        sync: false"));
        assertTrue(y.contains("key: SEED_ENABLED\n        value: \"false\""));
        assertTrue(y.contains("key: SSLCOMMERZ_SANDBOX\n        value: \"true\""));
        assertFalse(y.contains("Admin@123") || y.contains("qwerty") || y.contains("Demo@123"));
    }

    @Test
    void theDockerImageDefaultsToTheProductionProfile() throws Exception {
        assertTrue(read("Dockerfile").contains("ENV SPRING_PROFILES_ACTIVE=prod"));
        assertTrue(read("docker-compose.yml").contains("SPRING_PROFILES_ACTIVE: dev"), "local compose stays on dev");
    }

    @Test
    void envExampleShipsNoUsableSecretsOrPlaceholdersThatProductionWouldAccept() throws Exception {
        Properties env = props(".env.example");
        assertEquals("", env.getProperty("JWT_SECRET", "").trim());
        assertEquals("", env.getProperty("ADMIN_PASSWORD", "").trim());
        assertEquals("", env.getProperty("ADMIN_EMAIL", "").trim());
        assertEquals("true", env.getProperty("SSLCOMMERZ_SANDBOX"));
        assertFalse(read(".env.example").contains("ChangeThis"));
    }
}
