package com.skill2job.enums;

/** Status of a course the student picked up from a course suggestion. */
public enum CourseProgressStatus {
    IN_PROGRESS, COMPLETED;

    /** Used to drive the small progress bar on the profile page. */
    public int percent() {
        return this == COMPLETED ? 100 : 50;
    }
}
