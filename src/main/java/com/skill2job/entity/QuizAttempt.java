package com.skill2job.entity;

import com.skill2job.enums.AttemptStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One quiz attempt. The questions are fixed on the server when the attempt starts, so a student can
 * only ever be graded on the exact questions they were shown - never on ones they pick themselves.
 */
@Document(collection = "quiz_attempts")
public class QuizAttempt {

    @Id
    private String id;
    @Indexed
    private String studentId;
    private String skillId;
    private String skillName;
    private List<String> questionIds = new ArrayList<>();
    /** Chosen option per question (same order as questionIds); -1 = unanswered. */
    private List<Integer> answers = new ArrayList<>();
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;
    private int correctCount;
    private int total;
    private double scorePercent;
    private int levelAchieved;
    private boolean passed;
    private Instant startedAt;
    private Instant expiresAt;
    private Instant submittedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public List<String> getQuestionIds() {
        if (questionIds == null) { questionIds = new ArrayList<>(); }
        return questionIds;
    }
    public void setQuestionIds(List<String> questionIds) { this.questionIds = questionIds; }
    public List<Integer> getAnswers() {
        if (answers == null) { answers = new ArrayList<>(); }
        return answers;
    }
    public void setAnswers(List<Integer> answers) { this.answers = answers; }
    public AttemptStatus getStatus() { return status; }
    public void setStatus(AttemptStatus status) { this.status = status; }
    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public double getScorePercent() { return scorePercent; }
    public void setScorePercent(double scorePercent) { this.scorePercent = scorePercent; }
    public int getLevelAchieved() { return levelAchieved; }
    public void setLevelAchieved(int levelAchieved) { this.levelAchieved = levelAchieved; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
