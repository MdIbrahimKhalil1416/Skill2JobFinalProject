package com.skill2job.dto;

import jakarta.validation.constraints.NotBlank;

public record InterviewSelectRequest(@NotBlank(message = "Pick one of the proposed slots") String slot) {
}
