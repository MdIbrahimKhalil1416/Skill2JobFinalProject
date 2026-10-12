package com.skill2job.dto;

import com.skill2job.entity.JobTemplate;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.enums.JobType;

import java.time.Instant;
import java.util.List;

public record JobTemplateResponse(
        String id, String name, String title, String description, JobType type, String location,
        List<RequiredSkill> requiredSkills, Instant createdAt
) {
    public static JobTemplateResponse from(JobTemplate t) {
        return new JobTemplateResponse(t.getId(), t.getName(), t.getTitle(), t.getDescription(), t.getType(),
                t.getLocation(), List.copyOf(t.getRequiredSkills()), t.getCreatedAt());
    }
}
