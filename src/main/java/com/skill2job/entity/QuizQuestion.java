package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One multiple-choice question in a skill's quiz bank. correctIndex is NEVER sent to students before they submit. */
@Document(collection = "quiz_questions")
public class QuizQuestion {

    @Id
    private String id;
    @Indexed
    private String skillId;
    private String skillName;
    private String question;
    private List<String> options = new ArrayList<>();
    /** 0-based index into options. */
    private int correctIndex;
    private String explanation;
    /** 1 (easy) to 5 (hard). */
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
    public List<String> getOptions() {
        if (options == null) { options = new ArrayList<>(); }
        return options;
    }
    public void setOptions(List<String> options) { this.options = options; }
    public int getCorrectIndex() { return correctIndex; }
    public void setCorrectIndex(int correctIndex) { this.correctIndex = correctIndex; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
