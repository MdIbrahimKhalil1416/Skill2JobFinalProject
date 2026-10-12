package com.skill2job.dto;

import com.skill2job.entity.Interview;
import com.skill2job.enums.InterviewStatus;

import java.time.Instant;
import java.util.List;

public record InterviewResponse(
        String id, String applicationId, String jobId, String jobTitle, String companyName,
        String employerId, String studentId, String studentName, List<String> proposedSlots,
        String selectedSlot, InterviewStatus status, Instant createdAt, Instant updatedAt
) {
    public static InterviewResponse from(Interview i) {
        return new InterviewResponse(i.getId(), i.getApplicationId(), i.getJobId(), i.getJobTitle(),
                i.getCompanyName(), i.getEmployerId(), i.getStudentId(), i.getStudentName(),
                List.copyOf(i.getProposedSlots()), i.getSelectedSlot(), i.getStatus(), i.getCreatedAt(), i.getUpdatedAt());
    }
}
