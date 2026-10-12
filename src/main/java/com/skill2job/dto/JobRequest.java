package com.skill2job.dto;

import com.skill2job.enums.JobType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record JobRequest(
        @NotBlank(message = "Title is required") @Size(max = 120) String title,
        @NotBlank(message = "Description is required") @Size(max = 4000) String description,
        @NotNull(message = "Job type is required") JobType type,
        @NotBlank(message = "Location is required") @Size(max = 100) String location,
        @NotNull(message = "Deadline is required") LocalDate deadline,
        @NotEmpty(message = "Add at least one required skill")
        @Size(max = 15, message = "At most 15 skills per job") List<@Valid RequiredSkillRequest> requiredSkills
) {
}
