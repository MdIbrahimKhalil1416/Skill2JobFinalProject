package com.skill2job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(@NotBlank(message = "Message cannot be empty") @Size(max = 2000) String text) {
}
