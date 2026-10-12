package com.skill2job.dto;

import com.skill2job.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull(message = "Status is required") ApplicationStatus status) {
}
