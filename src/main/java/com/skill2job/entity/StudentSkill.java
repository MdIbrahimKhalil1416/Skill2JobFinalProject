package com.skill2job.entity;

import java.time.Instant;

/** Embedded inside StudentProfile: one skill the student has, with a level 1-5. */
public class StudentSkill {

    private String skillId;
    private String skillName;
    private int level;

    /** Set when an admin or another student vouches for this skill (shown as a badge, like LinkedIn). */
    private boolean endorsed;
    private String endorsedByName;
    private Instant endorsedAt;

    /** Set by the skill quiz (QuizService) when the student passes it: the level the score proves. */
    private boolean verified;
    private Integer verifiedLevel;
    private Double verifiedScore;
    private Instant verifiedAt;

    public StudentSkill() { }

    public StudentSkill(String skillId, String skillName, int level) {
        this.skillId = skillId;
        this.skillName = skillName;
        this.level = level;
    }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public boolean isEndorsed() { return endorsed; }
    public void setEndorsed(boolean endorsed) { this.endorsed = endorsed; }
    public String getEndorsedByName() { return endorsedByName; }
    public void setEndorsedByName(String endorsedByName) { this.endorsedByName = endorsedByName; }
    public Instant getEndorsedAt() { return endorsedAt; }
    public void setEndorsedAt(Instant endorsedAt) { this.endorsedAt = endorsedAt; }
    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
    public Integer getVerifiedLevel() { return verifiedLevel; }
    public void setVerifiedLevel(Integer verifiedLevel) { this.verifiedLevel = verifiedLevel; }
    public Double getVerifiedScore() { return verifiedScore; }
    public void setVerifiedScore(Double verifiedScore) { this.verifiedScore = verifiedScore; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }
}
