package com.skill2job.entity;

import com.skill2job.enums.JobStatus;
import com.skill2job.enums.JobType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "jobs")
public class Job {

    @Id
    private String id;

    @Indexed
    private String employerId;

    /** Copied from the employer profile so the job list needs no extra lookup. */
    private String companyName;
    private String title;
    private String description;
    private JobType type;
    private String location;
    private LocalDate deadline;

    @Indexed
    private JobStatus status = JobStatus.OPEN;

    private List<RequiredSkill> requiredSkills = new ArrayList<>();
    private Instant createdAt;
    /** When the job first became OPEN (immediately, or after the posting fee was paid). Job alerts use it. */
    private Instant publishedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmployerId() { return employerId; }
    public void setEmployerId(String employerId) { this.employerId = employerId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public JobType getType() { return type; }
    public void setType(JobType type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }
    public List<RequiredSkill> getRequiredSkills() {
        if (requiredSkills == null) { requiredSkills = new ArrayList<>(); }
        return requiredSkills;
    }
    public void setRequiredSkills(List<RequiredSkill> requiredSkills) { this.requiredSkills = requiredSkills; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
