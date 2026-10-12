package com.skill2job.config;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionRulesTest {

    private static final String GOOD_SECRET = "k8Jd93LmQx7VbN2pRt5YwZ0aHf6UcEi4SgTo1XyBqMn";   // random-looking, 43 chars

    private static Map<String, String> goodProd() {
        Map<String, String> p = new HashMap<>();
        p.put("app.jwt.secret", GOOD_SECRET);
        p.put("app.otp.log-to-console", "false");
        p.put("app.seed.print-credentials", "false");
        p.put("app.seed.enabled", "false");
        p.put("app.seed.allow-in-prod", "false");
        p.put("app.payment.sslcommerz.sandbox", "true");
        p.put("app.payment.sslcommerz.store-id", "testbox");
        p.put("app.payment.sslcommerz.store-password", "qwerty");
        p.put("app.payment.base-url", "https://skill2job.onrender.com");
        p.put("spring.mail.username", "me@example.org");
        return p;
    }

    // ------------------------------------------------------------ JWT secret

    @Test
    void goodJwtSecretIsAccepted() {
        assertNull(ProductionRules.jwtSecretProblem(GOOD_SECRET));
        assertNull(ProductionRules.jwtSecretProblem("Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MGFiY2RlZmdoaWprbG1ub3A="));
    }

    @Test
    void missingShortPlaceholderAndLowVarietySecretsAreRejected() {
        assertNotNull(ProductionRules.jwtSecretProblem(null));
        assertNotNull(ProductionRules.jwtSecretProblem(""));
        assertNotNull(ProductionRules.jwtSecretProblem("   "));
        assertNotNull(ProductionRules.jwtSecretProblem("too-short-secret"));
        assertNotNull(ProductionRules.jwtSecretProblem("a".repeat(64)));
        assertNotNull(ProductionRules.jwtSecretProblem("abababababababababababababababababab"));
    }

    @Test
    void theOldPublicDefaultsAndDocumentationPlaceholdersAreRejected() {
        // the development default that used to be the production fallback
        assertNotNull(ProductionRules.jwtSecretProblem("dev-only-secret-change-me-please-0123456789abcdef"));
        // the value that used to be in .env.example and docker-compose.yml
        assertNotNull(ProductionRules.jwtSecretProblem("replace-with-a-long-random-string-at-least-32-characters"));
        assertNotNull(ProductionRules.jwtSecretProblem("local-docker-secret-change-me-0123456789abcdef"));
    }

    // ------------------------------------------------------------ admin credentials

    @Test
    void strongAdminPasswordIsAcceptedAndKnownOnesAreNot() {
        assertNull(ProductionRules.adminPasswordProblem("Tr0ub4dor-and-3-horses"));
        for (String bad : new String[]{null, "", "  ", "Admin@123", "admin@123", "Demo@123", "ChangeThis@123", "password123",
                "short1A", "onlyletterslongenough", "12345678901234"}) {
            assertNotNull(ProductionRules.adminPasswordProblem(bad), "should be rejected: " + bad);
        }
    }

    @Test
    void adminEmailMustLookLikeAnEmail() {
        assertNull(ProductionRules.adminEmailProblem("boss@company.com"));
        assertNotNull(ProductionRules.adminEmailProblem(""));
        assertNotNull(ProductionRules.adminEmailProblem(null));
        assertNotNull(ProductionRules.adminEmailProblem("not-an-email"));
    }

    // ------------------------------------------------------------ admin bootstrap decision

    @Test
    void anExistingAccountIsAlwaysLeftUntouchedInEveryProfile() {
        for (boolean prod : new boolean[]{true, false}) {
            ProductionRules.AdminDecision d = ProductionRules.adminDecision(prod, true, 1, "boss@company.com", "Admin@123");
            assertEquals(ProductionRules.AdminAction.SKIP_EXISTS, d.action());
        }
    }

    @Test
    void productionCreatesAnAdminOnlyFromExplicitStrongCredentials() {
        assertEquals(ProductionRules.AdminAction.CREATE,
                ProductionRules.adminDecision(true, false, 0, "boss@company.com", "Tr0ub4dor-and-3-horses").action());
    }

    @Test
    void productionNeverCreatesAnAdminWithADefaultOrMissingPassword() {
        for (String pw : new String[]{"Admin@123", "", null, "ChangeThis@123", "short"}) {
            ProductionRules.AdminDecision d = ProductionRules.adminDecision(true, false, 0, "boss@company.com", pw);
            assertEquals(ProductionRules.AdminAction.FAIL, d.action(), "password: " + pw);
            assertTrue(d.reason().contains("ADMIN_PASSWORD"), d.reason());
        }
        assertEquals(ProductionRules.AdminAction.FAIL, ProductionRules.adminDecision(true, false, 0, "", "Tr0ub4dor-and-3-horses").action());
    }

    @Test
    void productionWithWeakCredentialsButAnAdminAlreadyPresentJustSkips() {
        assertEquals(ProductionRules.AdminAction.SKIP_ADMIN_PRESENT,
                ProductionRules.adminDecision(true, false, 2, "boss@company.com", "Admin@123").action());
    }

    @Test
    void developmentKeepsItsConvenientDefaultAdmin() {
        assertEquals(ProductionRules.AdminAction.CREATE,
                ProductionRules.adminDecision(false, false, 0, "admin@skill2job.com", "Admin@123").action());
        assertEquals(ProductionRules.AdminAction.SKIP_NOT_CONFIGURED,
                ProductionRules.adminDecision(false, false, 0, "", "").action());
    }

    // ------------------------------------------------------------ demo seeding

    @Test
    void demoSeedingIsOffByDefaultAndNeverSilentInProduction() {
        assertFalse(ProductionRules.demoSeedingAllowed(false, false, false));
        assertTrue(ProductionRules.demoSeedingAllowed(false, true, false), "dev convenience");
        assertFalse(ProductionRules.demoSeedingAllowed(true, true, false), "SEED_ENABLED alone cannot seed production");
        assertFalse(ProductionRules.demoSeedingAllowed(true, false, true));
        assertTrue(ProductionRules.demoSeedingAllowed(true, true, true), "only a deliberate double opt-in");
    }

    // ------------------------------------------------------------ whole production configuration

    @Test
    void aSafeProductionConfigurationHasNoErrors() {
        assertEquals(List.of(), ProductionRules.errors(goodProd()));
    }

    @Test
    void productionRefusesAMissingOrWeakJwtSecret() {
        Map<String, String> p = goodProd();
        p.put("app.jwt.secret", "");
        assertEquals(1, ProductionRules.errors(p).size());
        p.put("app.jwt.secret", "dev-only-secret-change-me-please-0123456789abcdef");
        assertEquals(1, ProductionRules.errors(p).size());
    }

    @Test
    void productionRefusesToLogOneTimeCodesOrDemoLogins() {
        Map<String, String> p = goodProd();
        p.put("app.otp.log-to-console", "true");
        p.put("app.seed.print-credentials", "TRUE");
        assertEquals(2, ProductionRules.errors(p).size());
    }

    @Test
    void sandboxPaymentsAreAllowedInProductionWithAWarningNotLive() {
        Map<String, String> p = goodProd();
        assertEquals(List.of(), ProductionRules.errors(p));
        assertTrue(ProductionRules.warnings(p).stream().anyMatch(w -> w.contains("SANDBOX")));
        p.put("app.payment.sslcommerz.sandbox", "");   // unset still means sandbox, never live
        assertEquals(List.of(), ProductionRules.errors(p));
    }

    @Test
    void livePaymentsNeedOwnCredentialsAndAPublicHttpsAddress() {
        Map<String, String> p = goodProd();
        p.put("app.payment.sslcommerz.sandbox", "false");
        assertEquals(2, ProductionRules.errors(p).size(), "demo store id + password");
        p.put("app.payment.sslcommerz.store-id", "mystore001");
        p.put("app.payment.sslcommerz.store-password", "S3cretStorePw");
        assertEquals(List.of(), ProductionRules.errors(p));
        p.put("app.payment.base-url", "http://localhost:8080");
        assertEquals(1, ProductionRules.errors(p).size());
        p.put("app.payment.base-url", "http://skill2job.onrender.com");
        assertEquals(1, ProductionRules.errors(p).size(), "http is not accepted for live payments");
        p.put("app.payment.sslcommerz.sandbox", "ture");   // a typo must not silently mean sandbox
        p.put("app.payment.base-url", "https://skill2job.onrender.com");
        p.put("app.payment.sslcommerz.store-id", "testbox");
        assertFalse(ProductionRules.errors(p).isEmpty());
    }

    @Test
    void riskyButAllowedSettingsProduceWarnings() {
        Map<String, String> p = goodProd();
        p.put("app.payment.base-url", "http://localhost:8080");
        p.put("spring.mail.username", "");
        p.put("app.seed.enabled", "true");
        p.put("app.seed.allow-in-prod", "true");
        List<String> w = ProductionRules.warnings(p);
        assertTrue(w.stream().anyMatch(x -> x.contains("APP_BASE_URL")));
        assertTrue(w.stream().anyMatch(x -> x.contains("MAIL_USERNAME")));
        assertTrue(w.stream().anyMatch(x -> x.contains("Demo data seeding")));
    }

    // ------------------------------------------------------------ email provider (Brevo / SMTP)

    @Test
    void aMissingBrevoKeyNeverStopsProductionItOnlyWarns() {
        Map<String, String> p = goodProd();
        p.put("spring.mail.username", "");
        p.put("app.mail.brevo.api-key", "");
        assertTrue(ProductionRules.errors(p).isEmpty(), "email is optional for startup");
        assertTrue(ProductionRules.warnings(p).stream().anyMatch(x -> x.contains("BREVO_API_KEY")));
    }

    @Test
    void smtpOnlyWarnsThatRenderBlocksSmtp() {
        Map<String, String> p = goodProd();          // MAIL_USERNAME set, no Brevo key
        assertTrue(ProductionRules.warnings(p).stream().anyMatch(x -> x.contains("block outbound SMTP")));
    }

    @Test
    void brevoWithAVerifiedSenderHasNoEmailWarningAndTheKeyIsNeverEchoed() {
        Map<String, String> p = goodProd();
        p.put("app.mail.brevo.api-key", "xkeysib-SECRETVALUE");
        p.put("app.mail.from", "me@example.org");
        List<String> w = ProductionRules.warnings(p);
        assertTrue(w.stream().noneMatch(x -> x.contains("BREVO") || x.contains("SMTP")), w.toString());
        assertTrue(w.stream().noneMatch(x -> x.contains("SECRETVALUE")));
        assertTrue(ProductionRules.errors(p).isEmpty());
    }

    @Test
    void brevoWithoutASenderAddressWarns() {
        Map<String, String> p = goodProd();
        p.put("app.mail.brevo.api-key", "xkeysib-x");
        p.put("app.mail.from", "no-reply@skill2job.com");
        assertTrue(ProductionRules.warnings(p).stream().anyMatch(x -> x.contains("MAIL_FROM")));
    }

    // ------------------------------------------------------------ log hygiene

    @Test
    void secretsInLoggedUrlsAreRedacted() {
        String msg = "I/O error on GET request for \"https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php"
                + "?val_id=V1&store_id=mystore&store_passwd=S3cret%40pw&format=json\": timeout";
        String clean = ProductionRules.redact(msg);
        assertFalse(clean.contains("S3cret"), clean);
        assertTrue(clean.contains("store_passwd=***"));
        assertTrue(clean.contains("val_id=V1"));
        assertNull(ProductionRules.redact(null));
    }
}
