package com.skill2job.dto;

import com.skill2job.entity.CourseProgress;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;

import java.util.List;

public record StudentProfileResponse(
        String id, String userId, String fullName, String department, Double cgpa,
        Integer graduationYear, String bio, List<StudentSkill> skills,
        boolean hasCv, String cvOriginalFilename,
        String githubUrl, String linkedinUrl, String websiteUrl,
        List<CourseProgress> courseProgress
) {
    public static StudentProfileResponse from(StudentProfile p) {
        return new StudentProfileResponse(p.getId(), p.getUserId(), p.getFullName(), p.getDepartment(),
                p.getCgpa(), p.getGraduationYear(), p.getBio(), List.copyOf(p.getSkills()),
                p.hasCv(), p.getCvOriginalFilename(),
                p.getGithubUrl(), p.getLinkedinUrl(), p.getWebsiteUrl(),
                List.copyOf(p.getCourseProgress()));
    }
}
