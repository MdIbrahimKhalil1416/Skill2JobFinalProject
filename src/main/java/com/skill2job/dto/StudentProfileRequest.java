package com.skill2job.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StudentProfileRequest(
        @Size(max = 80) String fullName,
        @Size(max = 80) String department,
        @DecimalMin(value = "0.0", message = "CGPA must be between 0 and 4") @DecimalMax(value = "4.0", message = "CGPA must be between 0 and 4") Double cgpa,
        @Min(value = 2000, message = "Graduation year looks wrong") @Max(value = 2100, message = "Graduation year looks wrong") Integer graduationYear,
        @Size(max = 500, message = "Bio must be at most 500 characters") String bio,
        @Pattern(regexp = "^(|https?://.*)$", message = "GitHub link must start with http:// or https://") @Size(max = 300) String githubUrl,
        @Pattern(regexp = "^(|https?://.*)$", message = "LinkedIn link must start with http:// or https://") @Size(max = 300) String linkedinUrl,
        @Pattern(regexp = "^(|https?://.*)$", message = "Website link must start with http:// or https://") @Size(max = 300) String websiteUrl
) {
}
