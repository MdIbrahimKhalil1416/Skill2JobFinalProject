package com.skill2job.dto;

import com.skill2job.enums.JobType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JobTemplateRequest(
        @NotBlank(message = "Template name is required") @Size(max = 100) String name,
        @NotBlank(message = "Title is required") @Size(max = 120) String title,
        @Size(max = 4000) String description,
        @NotNull(message = "Job type is required") JobType type,
        @NotBlank(message = "Location is required") @Size(max = 100) String location,
        @NotEmpty(message = "Add at least one required skill") @Size(max = 20) List<@Valid RequiredSkillRequest> requiredSkills
) {
}
