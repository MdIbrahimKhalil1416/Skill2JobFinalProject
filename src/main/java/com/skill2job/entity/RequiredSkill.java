package com.skill2job.entity;

/** Embedded inside Job: a skill the job needs. Plain class, no framework code. */
public class RequiredSkill {

    private String skillId;
    private String skillName;
    /** Minimum level 1-5. */
    private int requiredLevel;
    /** Importance 1-5, used in the weighted match formula. */
    private int weight;
    /** If true and the student lacks it, the student is not eligible. */
    private boolean mandatory;

    public RequiredSkill() { }

    public RequiredSkill(String skillId, String skillName, int requiredLevel, int weight, boolean mandatory) {
        this.skillId = skillId;
        this.skillName = skillName;
        this.requiredLevel = requiredLevel;
        this.weight = weight;
        this.mandatory = mandatory;
    }

    public String getSkillId() { return skillId; }
    public void setSkillId(String skillId) { this.skillId = skillId; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }
    public int getRequiredLevel() { return requiredLevel; }
    public void setRequiredLevel(int requiredLevel) { this.requiredLevel = requiredLevel; }
    public int getWeight() { return weight; }
    public void setWeight(int weight) { this.weight = weight; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
}
