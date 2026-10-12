package com.skill2job.config;

import com.skill2job.entity.User;
import com.skill2job.enums.Role;
import com.skill2job.repository.UserRepository;
import com.skill2job.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * DataSeeder.run() with only the collaborators it must touch wired in. Every other dependency (skills, jobs,
 * courses, demo seeders ...) is deliberately null: if production tried to seed demo records, or touched
 * anything it should not, the test would hit a NullPointerException and fail.
 */
class DataSeederBootstrapTest {

    static class RecordingAuth extends AuthService {
        final List<String> created = new ArrayList<>();
        RecordingAuth() { super(null, null, null, null, null, null, null, null); }
        @Override
        public User createUser(String name, String email, String rawPassword, Role role) {
            created.add(email + "|" + rawPassword + "|" + role);
            return new User();
        }
    }

    private final List<String> existingEmails = new ArrayList<>();
    private final List<User> admins = new ArrayList<>();
    private RecordingAuth auth;

    private UserRepository users() {
        return (UserRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByEmailIgnoreCase" -> existingEmails.contains(((String) args[0]).toLowerCase());
                    case "countByRole" -> (long) admins.size();
                    case "findByRole" -> Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Page.class},
                            (p2, m2, a2) -> m2.getName().equals("getContent") ? admins : null);
                    default -> throw new UnsupportedOperationException("unexpected repository call: " + method.getName());
                });
    }

    private DataSeeder seeder(String profile, String email, String password, boolean seedEnabled, boolean allowInProd) throws Exception {
        auth = new RecordingAuth();
        PasswordEncoder plain = new PasswordEncoder() {
            @Override public String encode(CharSequence raw) { return raw.toString(); }
            @Override public boolean matches(CharSequence raw, String encoded) { return raw.toString().equals(encoded); }
        };
        DataSeeder s = new DataSeeder(users(), null, null, null, null, null, null, auth, null, null, null, null, null, null,
                null, null, new ProductionConfigGuardTest.FakeEnv(profile), plain);
        set(s, "adminEmail", email);
        set(s, "adminPassword", password);
        set(s, "adminName", "Admin");
        set(s, "seedEnabled", seedEnabled);
        set(s, "allowSeedInProd", allowInProd);
        set(s, "seedQuestionBanks", false);
        return s;
    }

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = DataSeeder.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void productionWithNoAdminAndNoCredentialsRefusesToStartAndCreatesNothing() throws Exception {
        DataSeeder s = seeder("prod", "", "", false, false);
        try {
            s.run(null);
            fail("must refuse");
        } catch (ProductionConfigException e) {
            assertTrue(e.getMessage().contains("ADMIN_PASSWORD") || e.getMessage().contains("ADMIN_EMAIL"), e.getMessage());
        }
        assertEquals(0, auth.created.size());
    }

    @Test
    void productionNeverCreatesAnAdminWithTheOldDefaultPassword() throws Exception {
        DataSeeder s = seeder("prod", "admin@skill2job.com", "Admin@123", false, false);
        try {
            s.run(null);
            fail("must refuse the known default password");
        } catch (ProductionConfigException expected) {
            // refused
        }
        assertEquals(0, auth.created.size());
    }

    @Test
    void productionCreatesTheAdminFromExplicitStrongCredentials() throws Exception {
        DataSeeder s = seeder("prod", "Boss@Company.com", "Tr0ub4dor-and-3-horses", false, false);
        s.run(null);
        assertEquals(List.of("boss@company.com|Tr0ub4dor-and-3-horses|ADMIN"), auth.created);
    }

    @Test
    void anExistingAdminIsPreservedAndNeverRecreatedOrReset() throws Exception {
        existingEmails.add("boss@company.com");
        User old = new User();
        old.setEmail("boss@company.com");
        old.setPassword("Admin@123");           // an old deployment still on the default password: warn only
        admins.add(old);
        DataSeeder s = seeder("prod", "boss@company.com", "Tr0ub4dor-and-3-horses", false, false);
        s.run(null);
        assertEquals(0, auth.created.size());
        assertEquals("Admin@123", old.getPassword(), "an existing password is never changed automatically");
    }

    @Test
    void weakConfigurationDoesNotBreakAnInstallationThatAlreadyHasAnAdmin() throws Exception {
        User other = new User();
        other.setEmail("founder@company.com");
        other.setPassword("hash-of-a-strong-password");
        admins.add(other);
        seeder("prod", "", "", false, false).run(null);
        assertEquals(0, auth.created.size());
    }

    @Test
    void seedEnabledAloneNeverSeedsDemoDataInProduction() throws Exception {
        // skillRepository / featureDemoSeeder are null: any attempt to seed would throw a NullPointerException.
        seeder("prod", "boss@company.com", "Tr0ub4dor-and-3-horses", true, false).run(null);
        assertEquals(1, auth.created.size(), "only the admin");
    }

    @Test
    void developmentStillGetsItsConvenientAdmin() throws Exception {
        seeder("dev", "admin@skill2job.com", "Admin@123", false, false).run(null);
        assertEquals(List.of("admin@skill2job.com|Admin@123|ADMIN"), auth.created);
    }
}
