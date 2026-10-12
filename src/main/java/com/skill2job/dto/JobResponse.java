package com.skill2job.dto;

import com.skill2job.entity.Job;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.JobType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobResponse(
        String id, String employerId, String companyName, String title, String description,
        JobType type, String location, LocalDate deadline, JobStatus status,
        List<RequiredSkill> requiredSkills, Instant createdAt
) {
    public static JobResponse from(Job j) {
        return new JobResponse(j.getId(), j.getEmployerId(), j.getCompanyName(), j.getTitle(), j.getDescription(),
                j.getType(), j.getLocation(), j.getDeadline(), j.getStatus(),
                List.copyOf(j.getRequiredSkills()), j.getCreatedAt());
    }
}
