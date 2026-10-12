package com.skill2job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StudentSkillRequest(
        @NotBlank(message = "Skill is required") String skillId,
        @Min(value = 1, message = "Level must be 1-5") @Max(value = 5, message = "Level must be 1-5") int level
) {
}
