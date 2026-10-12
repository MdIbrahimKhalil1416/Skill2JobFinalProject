package com.skill2job.dto;

import java.util.List;

/** Courses recommended for one missing or weak skill. */
public record CourseSuggestion(String skillId, String skillName, int gap, int priority, List<CourseResponse> courses) {
}
