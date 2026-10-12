package com.skill2job.security;

import com.skill2job.enums.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    @Test
    void createdTokenIsValid() {
        JwtUtil jwt = new JwtUtil(SECRET, 60_000);
        String token = jwt.generateToken("user-1", "a@b.com", Role.STUDENT);
        assertEquals("user-1", jwt.validateAndGetUserId(token).orElseThrow());
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, 60_000);
        String token = jwt.generateToken("user-1", "a@b.com", Role.STUDENT);
        String other = jwt.generateToken("user-2", "c@d.com", Role.ADMIN);
        // Keep header + payload of token 1 but use the signature of token 2.
        String tampered = token.substring(0, token.lastIndexOf('.')) + other.substring(other.lastIndexOf('.'));
        assertTrue(jwt.validateAndGetUserId(tampered).isEmpty());
    }

    @Test
    void tokenFromAnotherSecretIsRejected() {
        String token = new JwtUtil(SECRET, 60_000).generateToken("user-1", "a@b.com", Role.ADMIN);
        JwtUtil other = new JwtUtil("another-secret-another-secret-another-1", 60_000);
        assertTrue(other.validateAndGetUserId(token).isEmpty());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtUtil jwt = new JwtUtil(SECRET, -1_000);
        String token = jwt.generateToken("user-1", "a@b.com", Role.STUDENT);
        assertTrue(jwt.validateAndGetUserId(token).isEmpty());
    }

    @Test
    void garbageIsRejected() {
        assertTrue(new JwtUtil(SECRET, 60_000).validateAndGetUserId("not-a-token").isEmpty());
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("short", 60_000));
    }
}
