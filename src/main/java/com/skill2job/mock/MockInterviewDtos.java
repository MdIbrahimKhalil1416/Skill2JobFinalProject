package com.skill2job.mock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class MockInterviewDtos {

    private MockInterviewDtos() { }

    public record SkillOption(String skillId, String skillName, long questionCount) { }

    public record StartRequest(
            @NotEmpty(message = "Pick at least one skill") @Size(max = 3, message = "Pick at most 3 skills") List<@NotBlank String> skillIds,
            @Min(value = 3, message = "At least 3 questions") @Max(value = 8, message = "At most 8 questions") int count) { }

    public record AnswerRequest(@Size(max = 4000, message = "Answers can be at most 4000 characters") String answer,
                                @Min(value = 1, message = "Rate yourself 1-5") @Max(value = 5, message = "Rate yourself 1-5") int selfRating) { }

    public record SubmitRequest(@NotNull @Size(min = 1, max = 8) List<@Valid AnswerRequest> answers) { }

    /** Key points and feedback are only filled in once the session is submitted. */
    public record ItemView(String questionId, String skillName, String question, String answer, int selfRating,
                           List<String> keyPoints, List<String> coveredPoints, List<String> missedPoints,
                           double coveragePercent, String feedback) { }

    public record SessionView(String id, List<String> skillNames, String status, List<ItemView> items,
                              double coveragePercent, double averageSelfRating, double score, String summary,
                              Instant startedAt, Instant completedAt) { }

    public record SessionSummary(String id, List<String> skillNames, String status, int questions, double score,
                                 Instant startedAt, Instant completedAt) { }

    /** Admin create/update. */
    public record QuestionRequest(@NotBlank String skillId,
                                  @NotBlank @Size(max = 500) String question,
                                  @NotEmpty @Size(max = 8) List<@NotBlank @Size(max = 200) String> keyPoints,
                                  @Min(1) @Max(5) int difficulty) { }

    public record QuestionAdminView(String id, String skillId, String skillName, String question,
                                    List<String> keyPoints, int difficulty) { }
}
