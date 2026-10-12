package com.skill2job.entity;

import com.skill2job.enums.AttemptStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One mock-interview practice session: questions, the student's written answers, self-ratings and feedback. */
@Document(collection = "mock_interview_sessions")
public class MockInterviewSession {

    @Id
    private String id;
    @Indexed
    private String studentId;
    private List<String> skillNames = new ArrayList<>();
    private List<Item> items = new ArrayList<>();
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;
    /** Average key-point coverage across answers, 0-100. */
    private double coveragePercent;
    /** Average self-rating, 1-5. */
    private double averageSelfRating;
    /** Combined readiness score, 0-100 (coverage weighted 70%, self-rating 30%). */
    private double score;
    private String summary;
    private Instant startedAt;
    private Instant completedAt;

    /** One question within the session. */
    public static class Item {
        private String questionId;
        private String skillName;
        private String question;
        private List<String> keyPoints = new ArrayList<>();
        private String answer;
        private int selfRating;
        private List<String> coveredPoints = new ArrayList<>();
        private List<String> missedPoints = new ArrayList<>();
        private double coveragePercent;
        private String feedback;

        public String getQuestionId() { return questionId; }
        public void setQuestionId(String questionId) { this.questionId = questionId; }
        public String getSkillName() { return skillName; }
        public void setSkillName(String skillName) { this.skillName = skillName; }
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public List<String> getKeyPoints() {
            if (keyPoints == null) { keyPoints = new ArrayList<>(); }
            return keyPoints;
        }
        public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }
        public String getAnswer() { return answer; }
        public void setAnswer(String answer) { this.answer = answer; }
        public int getSelfRating() { return selfRating; }
        public void setSelfRating(int selfRating) { this.selfRating = selfRating; }
        public List<String> getCoveredPoints() {
            if (coveredPoints == null) { coveredPoints = new ArrayList<>(); }
            return coveredPoints;
        }
        public void setCoveredPoints(List<String> coveredPoints) { this.coveredPoints = coveredPoints; }
        public List<String> getMissedPoints() {
            if (missedPoints == null) { missedPoints = new ArrayList<>(); }
            return missedPoints;
        }
        public void setMissedPoints(List<String> missedPoints) { this.missedPoints = missedPoints; }
        public double getCoveragePercent() { return coveragePercent; }
        public void setCoveragePercent(double coveragePercent) { this.coveragePercent = coveragePercent; }
        public String getFeedback() { return feedback; }
        public void setFeedback(String feedback) { this.feedback = feedback; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public List<String> getSkillNames() {
        if (skillNames == null) { skillNames = new ArrayList<>(); }
        return skillNames;
    }
    public void setSkillNames(List<String> skillNames) { this.skillNames = skillNames; }
    public List<Item> getItems() {
        if (items == null) { items = new ArrayList<>(); }
        return items;
    }
    public void setItems(List<Item> items) { this.items = items; }
    public AttemptStatus getStatus() { return status; }
    public void setStatus(AttemptStatus status) { this.status = status; }
    public double getCoveragePercent() { return coveragePercent; }
    public void setCoveragePercent(double coveragePercent) { this.coveragePercent = coveragePercent; }
    public double getAverageSelfRating() { return averageSelfRating; }
    public void setAverageSelfRating(double averageSelfRating) { this.averageSelfRating = averageSelfRating; }
    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
