package com.skill2job.controller;

import com.skill2job.dto.ApplicationResponse;
import com.skill2job.dto.CourseProgressRequest;
import com.skill2job.dto.JobMatchResponse;
import com.skill2job.dto.SavedJobResponse;
import com.skill2job.dto.StudentProfileRequest;
import com.skill2job.dto.StudentProfileResponse;
import com.skill2job.dto.StudentSkillRequest;
import com.skill2job.enums.JobType;
import com.skill2job.security.AuthUser;
import com.skill2job.service.ApplicationService;
import com.skill2job.service.DashboardService;
import com.skill2job.service.MatchingService;
import com.skill2job.service.SavedJobService;
import com.skill2job.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Student")
public class StudentController {

    private final StudentService studentService;
    private final MatchingService matchingService;
    private final ApplicationService applicationService;
    private final DashboardService dashboardService;
    private final SavedJobService savedJobService;

    public StudentController(StudentService studentService, MatchingService matchingService,
                             ApplicationService applicationService, DashboardService dashboardService,
                             SavedJobService savedJobService) {
        this.studentService = studentService;
        this.matchingService = matchingService;
        this.applicationService = applicationService;
        this.dashboardService = dashboardService;
        this.savedJobService = savedJobService;
    }

    @GetMapping("/profile")
    @Operation(summary = "My profile and skills")
    public StudentProfileResponse profile(@AuthenticationPrincipal AuthUser user) {
        return studentService.getProfile(user.id());
    }

    @PutMapping("/profile")
    @Operation(summary = "Update my profile")
    public StudentProfileResponse updateProfile(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody StudentProfileRequest request) {
        return studentService.updateProfile(user.id(), request);
    }

    @PostMapping("/skills")
    @Operation(summary = "Add a skill or change its level (1-5)")
    public StudentProfileResponse upsertSkill(@AuthenticationPrincipal AuthUser user,
                                              @Valid @RequestBody StudentSkillRequest request) {
        return studentService.upsertSkill(user.id(), request);
    }

    @DeleteMapping("/skills/{skillId}")
    @Operation(summary = "Remove a skill from my profile")
    public StudentProfileResponse removeSkill(@AuthenticationPrincipal AuthUser user, @PathVariable String skillId) {
        return studentService.removeSkill(user.id(), skillId);
    }

    @GetMapping("/recommended-jobs")
    @Operation(summary = "Open jobs sorted by my match % (best first)")
    public List<JobMatchResponse> recommended(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) JobType type,
                                              @RequestParam(required = false) String location,
                                              @RequestParam(required = false) String skillId) {
        return matchingService.recommended(user.id(), keyword, type, location, skillId);
    }

    @GetMapping("/applications")
    @Operation(summary = "My applications")
    public List<ApplicationResponse> applications(@AuthenticationPrincipal AuthUser user) {
        return applicationService.mine(user.id());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "My dashboard numbers and chart data")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthUser user) {
        return dashboardService.student(user.id());
    }

    @PutMapping("/course-progress")
    @Operation(summary = "Mark a suggested course In Progress or Completed")
    public StudentProfileResponse upsertCourseProgress(@AuthenticationPrincipal AuthUser user,
                                                        @Valid @RequestBody CourseProgressRequest request) {
        return studentService.upsertCourseProgress(user.id(), request);
    }

    @DeleteMapping("/course-progress/{courseId}")
    @Operation(summary = "Stop tracking a course's progress")
    public StudentProfileResponse removeCourseProgress(@AuthenticationPrincipal AuthUser user, @PathVariable String courseId) {
        return studentService.removeCourseProgress(user.id(), courseId);
    }

    @GetMapping("/saved-jobs")
    @Operation(summary = "My bookmarked jobs")
    public List<SavedJobResponse> savedJobs(@AuthenticationPrincipal AuthUser user) {
        return savedJobService.mine(user.id());
    }

    @PostMapping("/saved-jobs/{jobId}")
    @Operation(summary = "Bookmark a job to look at or apply to later")
    public SavedJobResponse saveJob(@AuthenticationPrincipal AuthUser user, @PathVariable String jobId) {
        return savedJobService.save(user.id(), jobId);
    }

    @DeleteMapping("/saved-jobs/{jobId}")
    @Operation(summary = "Remove a bookmarked job")
    public void unsaveJob(@AuthenticationPrincipal AuthUser user, @PathVariable String jobId) {
        savedJobService.unsave(user.id(), jobId);
    }
}
