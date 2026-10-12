package com.skill2job.controller;

import com.skill2job.cvbuilder.CvBuilderDtos;
import com.skill2job.cvbuilder.CvBuilderService;
import com.skill2job.gamification.GamificationService;
import com.skill2job.reviews.CompanyReviewDtos;
import com.skill2job.reviews.CompanyReviewService;
import com.skill2job.roadmap.RoadmapService;
import com.skill2job.security.AuthUser;
import com.skill2job.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Student career tools: learning roadmap, CV builder, achievements and company reviews. */
@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Student career tools")
public class StudentGrowthController {

    private final RoadmapService roadmapService;
    private final CvBuilderService cvBuilderService;
    private final GamificationService gamificationService;
    private final StudentService studentService;
    private final CompanyReviewService reviewService;

    public StudentGrowthController(RoadmapService roadmapService, CvBuilderService cvBuilderService,
                                   GamificationService gamificationService, StudentService studentService,
                                   CompanyReviewService reviewService) {
        this.roadmapService = roadmapService;
        this.cvBuilderService = cvBuilderService;
        this.gamificationService = gamificationService;
        this.studentService = studentService;
        this.reviewService = reviewService;
    }

    @GetMapping("/roadmap")
    @Operation(summary = "Week-by-week learning roadmap for a job (or for the most demanded skills if no job is given)")
    public RoadmapService.RoadmapResponse roadmap(@AuthenticationPrincipal AuthUser user,
                                                  @RequestParam(required = false) String jobId,
                                                  @RequestParam(defaultValue = "8") int hoursPerWeek) {
        return roadmapService.build(user.id(), jobId, hoursPerWeek);
    }

    @GetMapping("/cv")
    @Operation(summary = "Everything the CV builder needs (profile + CV sections)")
    public CvBuilderDtos.CvResponse cv(@AuthenticationPrincipal AuthUser user) {
        return cvBuilderService.get(user.id());
    }

    @PutMapping("/cv")
    @Operation(summary = "Save the CV builder sections")
    public CvBuilderDtos.CvResponse saveCv(@AuthenticationPrincipal AuthUser user,
                                           @Valid @RequestBody CvBuilderDtos.CvRequest request) {
        return cvBuilderService.save(user.id(), request);
    }

    @GetMapping("/achievements")
    @Operation(summary = "My XP, level, learning streak, badges and profile completeness")
    public GamificationService.Summary achievements(@AuthenticationPrincipal AuthUser user) {
        return gamificationService.summary(studentService.getOrCreate(user.id()));
    }

    @GetMapping("/reviews/reviewable")
    @Operation(summary = "Companies I can review (they responded to my application or interviewed me)")
    public List<CompanyReviewDtos.Reviewable> reviewable(@AuthenticationPrincipal AuthUser user) {
        return reviewService.reviewable(user.id());
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete my review")
    public void deleteReview(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        reviewService.deleteMine(user.id(), id);
    }
}
