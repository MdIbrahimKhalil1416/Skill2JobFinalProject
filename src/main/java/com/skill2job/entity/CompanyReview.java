package com.skill2job.entity;

import com.skill2job.enums.ExperienceType;
import com.skill2job.enums.ReviewStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A student's review of an employer. One per student per company; shown publicly only once APPROVED. */
@Document(collection = "company_reviews")
@CompoundIndex(name = "employer_student_unique", def = "{'employerId': 1, 'studentId': 1}", unique = true)
public class CompanyReview {

    @Id
    private String id;
    @Indexed
    private String employerId;
    private String companyName;
    private String studentId;
    private String studentName;
    /** If true the public page shows "Anonymous student" instead of the name. */
    private boolean anonymous;
    private int rating;
    private String title;
    private String pros;
    private String cons;
    private ExperienceType experienceType;
    private String jobTitle;
    @Indexed
    private ReviewStatus status = ReviewStatus.PENDING;
    private String moderationNote;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant moderatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEmployerId() { return employerId; }
    public void setEmployerId(String employerId) { this.employerId = employerId; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPros() { return pros; }
    public void setPros(String pros) { this.pros = pros; }
    public String getCons() { return cons; }
    public void setCons(String cons) { this.cons = cons; }
    public ExperienceType getExperienceType() { return experienceType; }
    public void setExperienceType(ExperienceType experienceType) { this.experienceType = experienceType; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public ReviewStatus getStatus() { return status; }
    public void setStatus(ReviewStatus status) { this.status = status; }
    public String getModerationNote() { return moderationNote; }
    public void setModerationNote(String moderationNote) { this.moderationNote = moderationNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(Instant moderatedAt) { this.moderatedAt = moderatedAt; }
}
