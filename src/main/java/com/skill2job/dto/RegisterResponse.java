package com.skill2job.dto;

/**
 * Returned by /api/auth/register.
 * <ul>
 *   <li>Email verification ON (EMAIL_VERIFICATION_ENABLED=true): no JWT yet - the account is unverified until the OTP is
 *       confirmed. emailSent=false means the account exists but the code email could not be sent right now; the user
 *       can request it again from the verification page. {@code auth} is null.</li>
 *   <li>Email verification OFF (the default): the account is active and {@code auth} carries the login token, so the
 *       frontend signs the user in straight away.</li>
 * </ul>
 */
public record RegisterResponse(String userId, String email, String message, long otpExpiresInSeconds, boolean emailSent,
                               AuthResponse auth) {
}
