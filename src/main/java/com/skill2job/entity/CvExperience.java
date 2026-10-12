package com.skill2job.entity;

/** Embedded in StudentProfile: one job, internship or volunteer role on the CV. */
public class CvExperience {
    private String title;
    private String organization;
    /** Free text such as "Jan 2025" - CVs commonly use month/year, not exact dates. */
    private String startDate;
    /** Free text; blank means "Present". */
    private String endDate;
    private String description;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
