package com.skill2job.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The pure decisions behind the production safety checks (no Spring, no database), so they can be unit tested
 * exhaustively - see ProductionRulesTest. {@link ProductionConfigGuard} feeds them from the real Environment and
 * {@link DataSeeder} uses {@link #demoSeedingAllowed} and {@link #adminDecision}.
 */
public final class ProductionRules {

    /** Passwords that are public knowledge (the old defaults, the demo password, the .env.example placeholder). */
    static final Set<String> KNOWN_PASSWORDS = Set.of("admin@123", "admin123", "demo@123", "password", "password1",
            "password123", "changeme", "changethis@123", "12345678", "123456789", "1234567890", "qwerty123");

    /** Text that only appears in example/placeholder/default secrets copied from documentation. */
    private static final List<String> PLACEHOLDER_TOKENS = List.of("change-me", "changeme", "changethis", "replace-with",
            "dev-only", "your-secret", "yoursecret", "example", "placeholder");

    public static final String SANDBOX_STORE_ID = "testbox";
    public static final String SANDBOX_STORE_PASSWORD = "qwerty";

    private ProductionRules() { }

    // ------------------------------------------------------------ secrets

    /** @return null when the JWT secret is acceptable for production, otherwise why not. */
    public static String jwtSecretProblem(String secret) {
        if (secret == null || secret.isBlank()) {
            return "JWT_SECRET is not set";
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            return "JWT_SECRET is shorter than 32 characters";
        }
        String lower = secret.toLowerCase(Locale.ROOT);
        for (String token : PLACEHOLDER_TOKENS) {
            if (lower.contains(token)) {
                return "JWT_SECRET looks like a placeholder or the public development default (contains \"" + token + "\")";
            }
        }
        if (secret.chars().distinct().count() < 10) {
            return "JWT_SECRET has too little variety (fewer than 10 different characters)";
        }
        return null;
    }

    /** @return null when the password is acceptable for a production admin account, otherwise why not. */
    public static String adminPasswordProblem(String password) {
        if (password == null || password.isBlank()) {
            return "ADMIN_PASSWORD is not set";
        }
        if (password.length() < 10) {
            return "ADMIN_PASSWORD is shorter than 10 characters";
        }
        String lower = password.trim().toLowerCase(Locale.ROOT);
        if (KNOWN_PASSWORDS.contains(lower)) {
            return "ADMIN_PASSWORD is a well-known default password";
        }
        for (String token : List.of("changethis", "change-me", "replace-with")) {
            if (lower.contains(token)) {
                return "ADMIN_PASSWORD looks like a placeholder";
            }
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            return "ADMIN_PASSWORD must contain both letters and digits";
        }
        return null;
    }

    public static String adminEmailProblem(String email) {
        if (email == null || email.isBlank()) {
            return "ADMIN_EMAIL is not set";
        }
        String e = email.trim();
        if (!e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return "ADMIN_EMAIL is not a valid email address";
        }
        return null;
    }

    // ------------------------------------------------------------ demo seeding

    /** Demo users, jobs, courses and other sample records: on only where SEED_ENABLED says so, and in production
     * only with the extra, deliberate SEED_ALLOW_IN_PROD=true. */
    public static boolean demoSeedingAllowed(boolean prod, boolean seedEnabled, boolean allowInProd) {
        return seedEnabled && (!prod || allowInProd);
    }

    // ------------------------------------------------------------ admin bootstrap

    public enum AdminAction { CREATE, SKIP_EXISTS, SKIP_NOT_CONFIGURED, SKIP_ADMIN_PRESENT, FAIL }

    public record AdminDecision(AdminAction action, String reason) { }

    /**
     * What to do about the bootstrap admin at startup. Never touches an existing account (no password reset, no
     * role change): if the configured email already exists, nothing happens.
     * In production a new admin is created only from an explicit, strong ADMIN_EMAIL / ADMIN_PASSWORD; if there
     * is no admin at all and those are missing or weak, startup is refused rather than creating a known-password admin.
     */
    public static AdminDecision adminDecision(boolean prod, boolean configuredEmailExists, long adminCount,
                                              String email, String password) {
        if (configuredEmailExists) {
            return new AdminDecision(AdminAction.SKIP_EXISTS, "the configured admin account already exists; left untouched");
        }
        if (!prod) {
            boolean configured = email != null && !email.isBlank() && password != null && !password.isBlank();
            return configured ? new AdminDecision(AdminAction.CREATE, "development admin")
                    : new AdminDecision(AdminAction.SKIP_NOT_CONFIGURED, "ADMIN_EMAIL / ADMIN_PASSWORD not set");
        }
        String problem = adminEmailProblem(email);
        if (problem == null) {
            problem = adminPasswordProblem(password);
        }
        if (problem == null) {
            return new AdminDecision(AdminAction.CREATE, "production admin from explicit configuration");
        }
        if (adminCount > 0) {
            return new AdminDecision(AdminAction.SKIP_ADMIN_PRESENT,
                    problem + ", so no new admin was created (an admin account already exists)");
        }
        return new AdminDecision(AdminAction.FAIL, "No admin account exists and one cannot be created safely: " + problem
                + ". Set ADMIN_EMAIL and a strong ADMIN_PASSWORD (10+ characters, letters and digits) and restart.");
    }

    // ------------------------------------------------------------ whole-configuration check

    /** Keys {@link ProductionConfigGuard} reads. */
    public static final List<String> KEYS = List.of("app.jwt.secret", "app.otp.log-to-console", "app.seed.print-credentials",
            "app.seed.enabled", "app.seed.allow-in-prod", "app.payment.sslcommerz.sandbox", "app.payment.sslcommerz.store-id",
            "app.payment.sslcommerz.store-password", "app.payment.base-url", "spring.mail.username",
            "app.mail.brevo.api-key", "app.mail.from", "app.auth.email-verification");

    private static String get(Map<String, String> p, String key) {
        String v = p.get(key);
        return v == null ? "" : v.trim();
    }

    private static boolean isTrue(Map<String, String> p, String key) {
        return "true".equalsIgnoreCase(get(p, key));
    }

    private static boolean isLocalOrInsecure(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return !u.startsWith("https://") || u.contains("localhost") || u.contains("127.0.0.1");
    }

    /** Problems that must stop a PRODUCTION start. */
    public static List<String> errors(Map<String, String> p) {
        List<String> errors = new ArrayList<>();
        String jwt = jwtSecretProblem(p.get("app.jwt.secret"));
        if (jwt != null) {
            errors.add(jwt + ". Set JWT_SECRET to a random string of at least 32 characters.");
        }
        if (isTrue(p, "app.otp.log-to-console")) {
            errors.add("OTP_LOG_TO_CONSOLE is true: signup and password-reset codes would be written to the logs.");
        }
        if (isTrue(p, "app.seed.print-credentials")) {
            errors.add("app.seed.print-credentials is true: demo logins would be written to the logs.");
        }
        boolean sandbox = get(p, "app.payment.sslcommerz.sandbox").isEmpty() || isTrue(p, "app.payment.sslcommerz.sandbox");
        if (!sandbox) {
            // LIVE (real-money) payments: never on demo credentials, never with callbacks to localhost/http.
            String storeId = get(p, "app.payment.sslcommerz.store-id");
            String storePassword = get(p, "app.payment.sslcommerz.store-password");
            if (storeId.isEmpty() || storeId.equalsIgnoreCase(SANDBOX_STORE_ID)) {
                errors.add("SSLCOMMERZ_SANDBOX=false (live payments) but SSLCOMMERZ_STORE_ID is missing or the public sandbox value.");
            }
            if (storePassword.isEmpty() || storePassword.equals(SANDBOX_STORE_PASSWORD)) {
                errors.add("SSLCOMMERZ_SANDBOX=false (live payments) but SSLCOMMERZ_STORE_PASSWORD is missing or the public sandbox value.");
            }
            if (isLocalOrInsecure(get(p, "app.payment.base-url"))) {
                errors.add("SSLCOMMERZ_SANDBOX=false (live payments) needs APP_BASE_URL to be your public https:// address (not localhost).");
            }
        }
        return errors;
    }

    /** Things worth a loud log line in production that do not have to stop it. */
    public static List<String> warnings(Map<String, String> p) {
        List<String> w = new ArrayList<>();
        boolean sandbox = get(p, "app.payment.sslcommerz.sandbox").isEmpty() || isTrue(p, "app.payment.sslcommerz.sandbox");
        if (sandbox) {
            w.add("Payments are in SSLCOMMERZ SANDBOX mode: no real money moves. Set SSLCOMMERZ_SANDBOX=false with your own "
                    + "store credentials only when you are ready for live payments.");
        }
        if (isLocalOrInsecure(get(p, "app.payment.base-url"))) {
            w.add("APP_BASE_URL is not a public https:// address: payment redirects and links in emails will not work for real users.");
        }
        boolean brevo = !get(p, "app.mail.brevo.api-key").isEmpty();
        boolean smtp = !get(p, "spring.mail.username").isEmpty();
        if (!brevo && !smtp) {
            w.add("No email provider is configured: set BREVO_API_KEY (recommended; works on Render) or MAIL_USERNAME / "
                    + "MAIL_PASSWORD (SMTP). Until then no email is sent (password-reset codes, alerts, status emails)"
                    + (isTrue(p, "app.auth.email-verification")
                        ? " and, because EMAIL_VERIFICATION_ENABLED=true, new users cannot verify their account."
                        : "; signup still works because email verification is off."));
        } else if (!brevo) {
            w.add("Email uses SMTP (MAIL_USERNAME). Render's free web services block outbound SMTP ports, so emails will "
                    + "fail there: set BREVO_API_KEY to send over HTTPS instead.");
        } else if (get(p, "app.mail.from").isEmpty() || get(p, "app.mail.from").equalsIgnoreCase("no-reply@skill2job.com")) {
            w.add("BREVO_API_KEY is set but no sender address is: set MAIL_FROM (or MAIL_USERNAME) to the sender email "
                    + "you verified in Brevo, otherwise Brevo rejects every email.");
        }
        if (isTrue(p, "app.seed.enabled") && isTrue(p, "app.seed.allow-in-prod")) {
            w.add("Demo data seeding is ENABLED in production (SEED_ENABLED and SEED_ALLOW_IN_PROD are both true): public demo "
                    + "accounts with a known password will be created in an empty database.");
        }
        return w;
    }

    // ------------------------------------------------------------ log hygiene

    /** Removes credentials from text that is about to be logged (URLs/messages from HTTP client errors). */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?i)(store_passwd|store_password|password|passwd|secret|token|api_key)=[^&\\s\"']*", "$1=***");
    }
}
