package com.skill2job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmployerProfileRequest(
        @NotBlank(message = "Company name is required") @Size(max = 120) String companyName,
        @Size(max = 200) String website,
        @Size(max = 1000) String description,
        @Size(max = 100) String location,
        @Size(max = 3000, message = "About us must be at most 3000 characters") String aboutUs
) {
}
