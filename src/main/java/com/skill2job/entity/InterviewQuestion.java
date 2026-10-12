package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Open-ended mock-interview question for one skill, with the key points a good answer should cover. */
@Document(collection = "interview_questions")
public class InterviewQuestion {

    @Id
    private String id;
    @Indexed
    private String skillId;
    private String skillName;
    private String question;
    private List<String> keyPoints = new ArrayList<>();
    private int difficulty = 2;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getKeyPoints() {
        if (keyPoints == null) { keyPoints = new ArrayList<>(); }
        return keyPoints;
    }
    public void setKeyPoints(List<String> keyPoints) { this.keyPoints = keyPoints; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
