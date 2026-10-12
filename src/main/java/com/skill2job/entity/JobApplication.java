package com.skill2job.entity;

import com.skill2job.enums.ApplicationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One student can apply to one job only once (unique compound index). */
@Document(collection = "applications")
@CompoundIndex(name = "job_student_unique", def = "{'jobId': 1, 'studentId': 1}", unique = true)
public class JobApplication {

    @Id
    private String id;
    private String jobId;
    private String jobTitle;
    private String companyName;
    private String employerId;
    private String studentId;
    private String studentName;
    private String studentEmail;
    /** Snapshot of the student's profile at the moment of applying, used for employer-side filtering. */
    private String department;
    private Double cgpa;
    private ApplicationStatus status = ApplicationStatus.APPLIED;
    /** Match % saved at the moment of applying. */
    private double matchPercent;
    private boolean eligible;
    private Instant appliedAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Double getCgpa() { return cgpa; }
    public void setCgpa(Double cgpa) { this.cgpa = cgpa; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public double getMatchPercent() { return matchPercent; }
    public void setMatchPercent(double matchPercent) { this.matchPercent = matchPercent; }
    public boolean isEligible() { return eligible; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
