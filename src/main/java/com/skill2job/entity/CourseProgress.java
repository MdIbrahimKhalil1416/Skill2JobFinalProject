package com.skill2job.entity;

import com.skill2job.enums.CourseProgressStatus;

import java.time.Instant;

/** Embedded inside StudentProfile: one course the student marked "In Progress" or "Completed". */
public class CourseProgress {

    private String courseId;
    private String courseTitle;
    private CourseProgressStatus status;
    private Instant updatedAt;

    public CourseProgress() { }

    public CourseProgress(String courseId, String courseTitle, CourseProgressStatus status, Instant updatedAt) {
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getCourseId() { return courseId; }
    public void setCourseId(String courseId) { this.courseId = courseId; }
    public String getCourseTitle() { return courseTitle; }
    public void setCourseTitle(String courseTitle) { this.courseTitle = courseTitle; }
    public CourseProgressStatus getStatus() { return status; }
    public void setStatus(CourseProgressStatus status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public int getPercent() { return status == null ? 0 : status.percent(); }
}
