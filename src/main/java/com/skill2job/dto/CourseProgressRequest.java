package com.skill2job.dto;

import com.skill2job.enums.CourseProgressStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CourseProgressRequest(
        @NotBlank(message = "Course is required") String courseId,
        @NotNull(message = "Status is required") CourseProgressStatus status
) {
}
