package com.skill2job.entity;

/** Embedded in StudentProfile: one project on the CV. */
public class CvProject {
    private String title;
    private String techStack;
    private String description;
    private String link;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTechStack() { return techStack; }
    public void setTechStack(String techStack) { this.techStack = techStack; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
}
