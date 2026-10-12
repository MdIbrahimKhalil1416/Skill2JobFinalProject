package com.skill2job.entity;

import com.skill2job.enums.JobType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A student's saved job search. JobAlertScheduler notifies them when new matching jobs are published. */
@Document(collection = "job_alerts")
public class JobAlert {

    @Id
    private String id;
    @Indexed
    private String studentId;
    private String name;
    private String keyword;
    private JobType type;
    private String location;
    private String skillId;
    private String skillName;
    /** Only alert for jobs where the student's match % is at least this (null = any). */
    private Integer minMatch;
    private boolean emailEnabled = true;
    private boolean active = true;
    private Instant createdAt;
    /** Jobs published after this instant are "new" for the next run. */
    private Instant lastCheckedAt;
    private Instant lastNotifiedAt;
    private int totalJobsSent;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public JobType getType() { return type; }
    public void setType(JobType type) { this.type = type; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public Integer getMinMatch() { return minMatch; }
    public void setMinMatch(Integer minMatch) { this.minMatch = minMatch; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean emailEnabled) { this.emailEnabled = emailEnabled; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public Instant getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(Instant lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
    public int getTotalJobsSent() { return totalJobsSent; }
    public void setTotalJobsSent(int totalJobsSent) { this.totalJobsSent = totalJobsSent; }
}
