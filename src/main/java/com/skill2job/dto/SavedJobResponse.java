package com.skill2job.dto;

import com.skill2job.entity.SavedJob;

import java.time.Instant;

public record SavedJobResponse(String id, String jobId, String jobTitle, String companyName, Instant savedAt) {
    public static SavedJobResponse from(SavedJob s) {
        return new SavedJobResponse(s.getId(), s.getJobId(), s.getJobTitle(), s.getCompanyName(), s.getSavedAt());
    }
}
