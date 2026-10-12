package com.skill2job.dto;

import com.skill2job.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 80, message = "Name is too long") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email is not valid") @Size(max = 120) String email,
        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 72, message = "Password must be 6-72 characters") String password,
        @NotNull(message = "Role is required") Role role,
        @Size(max = 120, message = "Company name is too long") String companyName
) {
}
