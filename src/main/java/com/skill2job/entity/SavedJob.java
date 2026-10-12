package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A job a student bookmarked to look at or apply to later. */
@Document(collection = "saved_jobs")
@CompoundIndex(name = "student_job_unique", def = "{'studentId': 1, 'jobId': 1}", unique = true)
public class SavedJob {

    @Id
    private String id;
    private String studentId;
    private String jobId;
    private String jobTitle;
    private String companyName;
    private Instant savedAt;

    /** Set once a deadline-approaching reminder has been sent, so it is not sent twice. */
    private boolean deadlineReminderSent;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public Instant getSavedAt() { return savedAt; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }
    public boolean isDeadlineReminderSent() { return deadlineReminderSent; }
    public void setDeadlineReminderSent(boolean deadlineReminderSent) { this.deadlineReminderSent = deadlineReminderSent; }
}
