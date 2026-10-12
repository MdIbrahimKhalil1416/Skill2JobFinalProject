package com.skill2job.entity;

/** Embedded in StudentProfile: one certificate/award on the CV. */
public class CvCertification {
    private String name;
    private String issuer;
    private String year;
    private String link;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
}
