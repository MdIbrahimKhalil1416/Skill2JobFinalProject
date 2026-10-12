package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Skills are EMBEDDED: they are always loaded together with the profile. */
@Document(collection = "student_profiles")
public class StudentProfile {

    @Id
    private String id;

    @Indexed(unique = true)
    private String userId;

    private String fullName;
    private String department;
    private Double cgpa;
    private Integer graduationYear;
    private String bio;
    private List<StudentSkill> skills = new ArrayList<>();

    /** The name shown to the student/employer ("John_CV.pdf"). Never used to build a file path. */
    private String cvOriginalFilename;
    /** The generated, collision-free name the file is actually stored under on disk. */
    private String cvStoredFilename;
    private Instant cvUploadedAt;

    // ---- Portfolio links (shown on the profile; only http(s) links are accepted) ----
    private String githubUrl;
    private String linkedinUrl;
    private String websiteUrl;

    /** One entry per course the student started from a course suggestion ("In Progress"/"Completed"). */
    private List<CourseProgress> courseProgress = new ArrayList<>();

    /** Student Pro runs until this moment (null = never bought). Set ONLY by PaymentService after SSLCommerz
     * has confirmed the payment - no other code path writes it. */
    private Instant proExpiresAt;
    /** Tran ids of the Student Pro payments already added to proExpiresAt (same atomic update as the extension). */
    private List<String> appliedPaymentIds = new ArrayList<>();

    // ---- Gamification (see GamificationService) ----
    /** Experience points earned from learning activity (quizzes, courses, mock interviews, ...). */
    private int xp;
    /** Days (yyyy-MM-dd, Asia/Dhaka) with any learning activity - the learning streak is computed from these. */
    private List<String> activityDays = new ArrayList<>();
    /** One-time XP awards already granted (e.g. "COURSE_DONE:<id>"), so the same action never pays twice. */
    private List<String> awardedKeys = new ArrayList<>();

    // ---- CV builder sections (see CvBuilderService); everything else on the CV comes from the profile ----
    private String headline;
    private String phone;
    private String city;
    private List<CvProject> projects = new ArrayList<>();
    private List<CvExperience> experiences = new ArrayList<>();
    private List<CvEducation> education = new ArrayList<>();
    private List<CvCertification> certifications = new ArrayList<>();
    private List<String> languages = new ArrayList<>();
    /** Which CV template the student picked last ("modern", "classic", "minimal"). */
    private String cvTemplate;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Double getCgpa() { return cgpa; }
    public void setCgpa(Double cgpa) { this.cgpa = cgpa; }
    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public List<StudentSkill> getSkills() {
        if (skills == null) { skills = new ArrayList<>(); }
        return skills;
    }
    public void setSkills(List<StudentSkill> skills) { this.skills = skills; }
    public String getCvOriginalFilename() { return cvOriginalFilename; }
    public void setCvOriginalFilename(String cvOriginalFilename) { this.cvOriginalFilename = cvOriginalFilename; }
    public String getCvStoredFilename() { return cvStoredFilename; }
    public void setCvStoredFilename(String cvStoredFilename) { this.cvStoredFilename = cvStoredFilename; }
    public Instant getCvUploadedAt() { return cvUploadedAt; }
    public void setCvUploadedAt(Instant cvUploadedAt) { this.cvUploadedAt = cvUploadedAt; }
    public boolean hasCv() { return cvStoredFilename != null && !cvStoredFilename.isBlank(); }
    public String getGithubUrl() { return githubUrl; }
    public void setGithubUrl(String githubUrl) { this.githubUrl = githubUrl; }
    public String getLinkedinUrl() { return linkedinUrl; }
    public void setLinkedinUrl(String linkedinUrl) { this.linkedinUrl = linkedinUrl; }
    public String getWebsiteUrl() { return websiteUrl; }
    public void setWebsiteUrl(String websiteUrl) { this.websiteUrl = websiteUrl; }
    public List<CourseProgress> getCourseProgress() {
        if (courseProgress == null) { courseProgress = new ArrayList<>(); }
        return courseProgress;
    }
    public void setCourseProgress(List<CourseProgress> courseProgress) { this.courseProgress = courseProgress; }

    public List<String> getAppliedPaymentIds() { return appliedPaymentIds; }
    public void setAppliedPaymentIds(List<String> appliedPaymentIds) { this.appliedPaymentIds = appliedPaymentIds == null ? new ArrayList<>() : appliedPaymentIds; }
    public Instant getProExpiresAt() { return proExpiresAt; }
    public void setProExpiresAt(Instant proExpiresAt) { this.proExpiresAt = proExpiresAt; }
    public boolean isProActiveAt(Instant now) { return proExpiresAt != null && now.isBefore(proExpiresAt); }
    public boolean isProActive() { return isProActiveAt(Instant.now()); }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }
    public List<String> getActivityDays() {
        if (activityDays == null) { activityDays = new ArrayList<>(); }
        return activityDays;
    }
    public void setActivityDays(List<String> activityDays) { this.activityDays = activityDays; }
    public List<String> getAwardedKeys() {
        if (awardedKeys == null) { awardedKeys = new ArrayList<>(); }
        return awardedKeys;
    }
    public void setAwardedKeys(List<String> awardedKeys) { this.awardedKeys = awardedKeys; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public List<CvProject> getProjects() {
        if (projects == null) { projects = new ArrayList<>(); }
        return projects;
    }
    public void setProjects(List<CvProject> projects) { this.projects = projects; }
    public List<CvExperience> getExperiences() {
        if (experiences == null) { experiences = new ArrayList<>(); }
        return experiences;
    }
    public void setExperiences(List<CvExperience> experiences) { this.experiences = experiences; }
    public List<CvEducation> getEducation() {
        if (education == null) { education = new ArrayList<>(); }
        return education;
    }
    public void setEducation(List<CvEducation> education) { this.education = education; }
    public List<CvCertification> getCertifications() {
        if (certifications == null) { certifications = new ArrayList<>(); }
        return certifications;
    }
    public void setCertifications(List<CvCertification> certifications) { this.certifications = certifications; }
    public List<String> getLanguages() {
        if (languages == null) { languages = new ArrayList<>(); }
        return languages;
    }
    public void setLanguages(List<String> languages) { this.languages = languages; }
    public String getCvTemplate() { return cvTemplate; }
    public void setCvTemplate(String cvTemplate) { this.cvTemplate = cvTemplate; }
}
