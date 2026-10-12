package com.skill2job.security;

import com.skill2job.enums.Role;

/** The logged-in user, available in controllers through @AuthenticationPrincipal. */
public record AuthUser(String id, String email, String name, Role role) {
}
