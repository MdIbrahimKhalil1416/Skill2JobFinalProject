package com.skill2job.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(
        @NotBlank(message = "Email is required") @Email(message = "Email is not valid") String email
) {
}
