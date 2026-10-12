package com.skill2job.matching;

/**
 * One row of the skill-gap report.
 * gap = requiredLevel - studentLevel (0 when the skill is strong).
 * priority = weight * gap, used to decide which skill to learn first.
 */
public record SkillGap(
        String skillId,
        String skillName,
        int requiredLevel,
        int studentLevel,
        int weight,
        boolean mandatory,
        int gap,
        int priority
) {
}
