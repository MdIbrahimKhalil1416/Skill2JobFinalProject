package com.skill2job.reviews;

import com.skill2job.enums.ExperienceType;
import com.skill2job.enums.ReviewStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class CompanyReviewDtos {

    private CompanyReviewDtos() { }

    public record ReviewRequest(@Min(value = 1, message = "Rating must be 1-5") @Max(value = 5, message = "Rating must be 1-5") int rating,
                                @NotBlank(message = "Give your review a title") @Size(max = 100) String title,
                                @Size(max = 1000) String pros,
                                @Size(max = 1000) String cons,
                                @NotNull(message = "Choose the kind of experience") ExperienceType experienceType,
                                @Size(max = 120) String jobTitle,
                                boolean anonymous) { }

    public record ModerationRequest(@NotNull ReviewStatus status, @Size(max = 300) String note) { }

    /** Public view: the reviewer's name is replaced when they chose to stay anonymous. */
    public record PublicReview(String id, String employerId, String companyName, String reviewerName, int rating,
                               String title, String pros, String cons, ExperienceType experienceType, String jobTitle,
                               Instant createdAt) { }

    /** Owner/admin view, including moderation details. */
    public record FullReview(String id, String employerId, String companyName, String studentId, String studentName,
                             boolean anonymous, int rating, String title, String pros, String cons,
                             ExperienceType experienceType, String jobTitle, ReviewStatus status, String moderationNote,
                             Instant createdAt, Instant updatedAt, Instant moderatedAt) { }

    /** A company the student is allowed to review, and their existing review if any. */
    public record Reviewable(String employerId, String companyName, List<String> jobTitles, ExperienceType suggestedType,
                             FullReview myReview) { }
}
