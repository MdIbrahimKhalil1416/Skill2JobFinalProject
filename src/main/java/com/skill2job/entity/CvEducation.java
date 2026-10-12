package com.skill2job.entity;

/** Embedded in StudentProfile: one degree/school on the CV. */
public class CvEducation {
    private String degree;
    private String institution;
    private String startYear;
    private String endYear;
    /** e.g. "CGPA 3.62/4.00" or "GPA 5.00". */
    private String result;

    public String getDegree() { return degree; }
    public void setDegree(String degree) { this.degree = degree; }
    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }
    public String getStartYear() { return startYear; }
    public void setStartYear(String startYear) { this.startYear = startYear; }
    public String getEndYear() { return endYear; }
    public void setEndYear(String endYear) { this.endYear = endYear; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
}
