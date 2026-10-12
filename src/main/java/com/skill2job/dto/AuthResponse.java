package com.skill2job.dto;

public record AuthResponse(String token, long expiresInMs, UserResponse user) {
}
