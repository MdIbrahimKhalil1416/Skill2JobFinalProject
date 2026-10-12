package com.skill2job.dto;

import com.skill2job.entity.User;
import com.skill2job.enums.Role;

import java.time.Instant;

/** Public view of a user. The password is never included. */
public record UserResponse(String id, String name, String email, Role role, boolean active,
                           boolean emailVerified, Instant createdAt) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isActive(),
                u.isEmailVerified(), u.getCreatedAt());
    }
}
