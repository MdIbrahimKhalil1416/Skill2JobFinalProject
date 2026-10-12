package com.skill2job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InterviewProposeRequest(
        @NotBlank(message = "Application is required") String applicationId,
        @NotEmpty(message = "Propose at least one time slot") @Size(max = 5, message = "At most 5 slots") List<@NotBlank String> slots
) {
}
