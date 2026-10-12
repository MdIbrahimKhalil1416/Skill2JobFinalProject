package com.skill2job.controller;

import com.skill2job.analytics.EmployerAnalyticsService;
import com.skill2job.reviews.CompanyReviewService;
import com.skill2job.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employer")
@PreAuthorize("hasRole('EMPLOYER')")
@Tag(name = "Employer analytics")
public class EmployerInsightsController {

    private final EmployerAnalyticsService analyticsService;
    private final CompanyReviewService reviewService;

    public EmployerInsightsController(EmployerAnalyticsService analyticsService, CompanyReviewService reviewService) {
        this.analyticsService = analyticsService;
        this.reviewService = reviewService;
    }

    @GetMapping("/analytics")
    @Operation(summary = "Hiring funnel, time to hire, applicant skills and per-job numbers (optionally for one job)")
    public EmployerAnalyticsService.Analytics analytics(@AuthenticationPrincipal AuthUser user,
                                                       @RequestParam(required = false) String jobId,
                                                       @RequestParam(defaultValue = "12") int weeks) {
        return analyticsService.analytics(user.id(), jobId, weeks);
    }

    @GetMapping("/reviews")
    @Operation(summary = "My company's approved reviews and rating")
    public CommunityController.CompanyReviews reviews(@AuthenticationPrincipal AuthUser user) {
        return new CommunityController.CompanyReviews(reviewService.summary(user.id()), reviewService.publicReviews(user.id()));
    }
}
