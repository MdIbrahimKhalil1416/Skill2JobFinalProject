package com.skill2job.dto;

public record ResendOtpResponse(String message, long otpExpiresInSeconds, boolean emailSent) {
}
