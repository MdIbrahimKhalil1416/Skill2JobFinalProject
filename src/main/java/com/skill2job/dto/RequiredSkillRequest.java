package com.skill2job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RequiredSkillRequest(
        @NotBlank(message = "Skill is required") String skillId,
        @Min(value = 1, message = "Required level must be 1-5") @Max(value = 5, message = "Required level must be 1-5") int requiredLevel,
        @Min(value = 1, message = "Weight must be 1-5") @Max(value = 5, message = "Weight must be 1-5") int weight,
        boolean mandatory
) {
}
