package com.skill2job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CourseRequest(
        @NotBlank(message = "Title is required") @Size(max = 150) String title,
        @NotBlank(message = "Platform is required") @Size(max = 60) String platform,
        @NotBlank(message = "URL is required") @Size(max = 300)
        @Pattern(regexp = "^https?://.+", message = "URL must start with http:// or https://") String url,
        @Min(value = 1, message = "Level must be 1-5") @Max(value = 5, message = "Level must be 1-5") int level,
        boolean free,
        @NotEmpty(message = "Select at least one skill") List<@NotBlank String> skillIds
) {
}
