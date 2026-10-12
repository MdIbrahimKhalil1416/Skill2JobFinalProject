package com.skill2job.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Request/response records of the skill-test API, grouped in one file because they are small. */
public final class QuizDtos {

    private QuizDtos() { }

    /** One skill on the "Skill tests" page. */
    public record SkillQuizSummary(String skillId, String skillName, String category, long questionCount,
                                   boolean hasSkill, Integer claimedLevel, boolean verified, Integer verifiedLevel,
                                   Double verifiedScore, Double lastScore, int attempts,
                                   Instant retakeAvailableAt, boolean inProgress) { }

    /** A question as shown while taking the test - never includes the correct answer. */
    public record QuestionView(String id, String question, List<String> options, int difficulty) { }

    public record StartResponse(String attemptId, String skillId, String skillName, Instant expiresAt,
                                long secondsLeft, List<QuestionView> questions) { }

    /** answers.get(i) = chosen option index for the i-th question shown (null or -1 = skipped). */
    public record SubmitRequest(@NotNull(message = "Answers are required") @Size(max = 50) List<Integer> answers) { }

    public record ReviewItem(String question, List<String> options, Integer chosen, int correctIndex,
                             boolean correct, String explanation) { }

    public record ResultResponse(String attemptId, String skillId, String skillName, int correctCount, int total,
                                 double scorePercent, int levelAchieved, boolean passed, Integer verifiedLevel,
                                 int xpEarned, List<ReviewItem> review) { }

    public record AttemptView(String id, String skillName, double scorePercent, int levelAchieved, boolean passed,
                              String status, Instant startedAt, Instant submittedAt) { }

    /** Admin: create/update a question. */
    public record QuestionRequest(
            @NotBlank(message = "Pick a skill") String skillId,
            @NotBlank(message = "Write the question") @Size(max = 500) String question,
            @NotNull @Size(min = 2, max = 6, message = "Give 2 to 6 options") List<@NotBlank @Size(max = 200) String> options,
            @Min(value = 0, message = "Mark the correct option") int correctIndex,
            @Size(max = 600) String explanation,
            @Min(1) @Max(5) int difficulty) { }

    /** Admin view - includes the correct answer. */
    public record QuestionAdminView(String id, String skillId, String skillName, String question, List<String> options,
                                    int correctIndex, String explanation, int difficulty) { }
}
