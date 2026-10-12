package com.skill2job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SkillRequest(
        @NotBlank(message = "Skill name is required") @Size(max = 50, message = "Skill name is too long") String name,
        @NotBlank(message = "Category is required") @Size(max = 50) String category,
        @Size(max = 15, message = "At most 15 aliases") List<@NotBlank @Size(max = 50) String> aliases
) {
}
