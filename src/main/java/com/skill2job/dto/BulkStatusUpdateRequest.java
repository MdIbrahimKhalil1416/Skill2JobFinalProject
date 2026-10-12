package com.skill2job.dto;

import com.skill2job.enums.ApplicationStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkStatusUpdateRequest(
        @NotEmpty(message = "Select at least one application") @Size(max = 200) List<String> applicationIds,
        @NotNull(message = "Status is required") ApplicationStatus status
) {
}
