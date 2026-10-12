package com.skill2job.entity;

import com.skill2job.enums.JobType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** A reusable skill-set template an employer saves to prefill future job posts. */
@Document(collection = "job_templates")
public class JobTemplate {

    @Id
    private String id;

    @Indexed
    private String employerId;

    private String name;
    private String title;
    private String description;
    private JobType type;
    private String location;
    private List<RequiredSkill> requiredSkills = new ArrayList<>();
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmployerId() { return employerId; }
    public void setEmployerId(String employerId) { this.employerId = employerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public JobType getType() { return type; }
    public void setType(JobType type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public List<RequiredSkill> getRequiredSkills() {
        if (requiredSkills == null) { requiredSkills = new ArrayList<>(); }
        return requiredSkills;
    }
    public void setRequiredSkills(List<RequiredSkill> requiredSkills) { this.requiredSkills = requiredSkills; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
