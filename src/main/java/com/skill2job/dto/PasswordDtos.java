package com.skill2job.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request/response records for the forgot-password and change-password flows. */
public final class PasswordDtos {

    private PasswordDtos() { }

    public record ForgotPasswordRequest(@NotBlank(message = "Email is required") @Email(message = "Email is not valid") String email) { }

    public record ResetPasswordRequest(
            @NotBlank(message = "Email is required") @Email(message = "Email is not valid") String email,
            @NotBlank(message = "Enter the 6-digit code") @Pattern(regexp = "\\d{6}", message = "The code has 6 digits") String code,
            @NotBlank(message = "Enter a new password") @Size(min = 6, max = 72, message = "Password must be 6-72 characters") String newPassword) { }

    public record ChangePasswordRequest(
            @NotBlank(message = "Enter your current password") String currentPassword,
            @NotBlank(message = "Enter a new password") @Size(min = 6, max = 72, message = "Password must be 6-72 characters") String newPassword) { }

    public record MessageResponse(String message) { }
}
