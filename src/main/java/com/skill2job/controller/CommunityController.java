package com.skill2job.controller;

import com.skill2job.gamification.GamificationService;
import com.skill2job.reviews.CompanyReviewDtos;
import com.skill2job.reviews.CompanyReviewService;
import com.skill2job.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Company reviews (public read, student write), featured reviews and the XP leaderboard. */
@RestController
@Tag(name = "Community")
public class CommunityController {

    private final CompanyReviewService reviewService;
    private final GamificationService gamificationService;

    public CommunityController(CompanyReviewService reviewService, GamificationService gamificationService) {
        this.reviewService = reviewService;
        this.gamificationService = gamificationService;
    }

    public record CompanyReviews(CompanyReviewService.RatingSummary summary, List<CompanyReviewDtos.PublicReview> reviews) { }

    @GetMapping("/api/companies/{employerId}/reviews")
    @Operation(summary = "Approved reviews and the rating summary of a company (public)")
    public CompanyReviews companyReviews(@PathVariable String employerId) {
        return new CompanyReviews(reviewService.summary(employerId), reviewService.publicReviews(employerId));
    }

    @PostMapping("/api/companies/{employerId}/reviews")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Write or edit my review of a company (goes to admin moderation)")
    public CompanyReviewDtos.FullReview writeReview(@AuthenticationPrincipal AuthUser user, @PathVariable String employerId,
                                                    @Valid @RequestBody CompanyReviewDtos.ReviewRequest request) {
        return reviewService.upsert(user.id(), employerId, request);
    }

    @GetMapping("/api/public/featured-reviews")
    @Operation(summary = "Recent 4-5 star approved reviews for the landing page (public)")
    public List<CompanyReviewDtos.PublicReview> featured() {
        return reviewService.featured();
    }

    @GetMapping("/api/leaderboard")
    @Operation(summary = "Top 20 students by XP (any signed-in user)")
    public List<GamificationService.LeaderboardRow> leaderboard() {
        return gamificationService.leaderboard();
    }
}
