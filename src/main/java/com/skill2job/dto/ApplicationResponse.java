package com.skill2job.dto;

import com.skill2job.entity.JobApplication;
import com.skill2job.enums.ApplicationStatus;

import java.time.Instant;

public record ApplicationResponse(
        String id, String jobId, String jobTitle, String companyName, String studentId, String studentName,
        String studentEmail, String department, Double cgpa, ApplicationStatus status, double matchPercent,
        boolean eligible, Instant appliedAt, Instant updatedAt
) {
    public static ApplicationResponse from(JobApplication a) {
        return new ApplicationResponse(a.getId(), a.getJobId(), a.getJobTitle(), a.getCompanyName(),
                a.getStudentId(), a.getStudentName(), a.getStudentEmail(), a.getDepartment(), a.getCgpa(),
                a.getStatus(), a.getMatchPercent(), a.isEligible(), a.getAppliedAt(), a.getUpdatedAt());
    }
}
