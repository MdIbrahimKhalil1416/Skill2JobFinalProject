package com.skill2job.entity;

import com.skill2job.enums.InterviewStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Employer proposes interview time slots for an application; the student picks one. */
@Document(collection = "interviews")
public class Interview {

    @Id
    private String id;

    @Indexed
    private String applicationId;
    private String jobId;
    private String jobTitle;
    private String companyName;
    private String employerId;

    @Indexed
    private String studentId;
    private String studentName;
    private String studentEmail;

    /** ISO-8601 datetime strings proposed by the employer. */
    private List<String> proposedSlots = new ArrayList<>();
    private String selectedSlot;
    private InterviewStatus status = InterviewStatus.PROPOSED;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getEmployerId() { return employerId; }
    public void setEmployerId(String employerId) { this.employerId = employerId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    public List<String> getProposedSlots() {
        if (proposedSlots == null) { proposedSlots = new ArrayList<>(); }
        return proposedSlots;
    }
    public void setProposedSlots(List<String> proposedSlots) { this.proposedSlots = proposedSlots; }
    public String getSelectedSlot() { return selectedSlot; }
    public void setSelectedSlot(String selectedSlot) { this.selectedSlot = selectedSlot; }
    public InterviewStatus getStatus() { return status; }
    public void setStatus(InterviewStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
