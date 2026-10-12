package com.skill2job.matching;

import com.skill2job.entity.RequiredSkill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The heart of Skill2Job: weighted, rule-based matching (no AI).
 *
 * For every required skill:
 *     skillScore = min(studentLevel / requiredLevel, 1)      (0 if the student lacks it)
 * Then:
 *     Match % = sum(skillScore * weight) / sum(weight) * 100   (rounded to 1 decimal)
 *
 * If a mandatory skill is completely missing, eligible = false (the % is still shown).
 * Pure Java with no Spring code, so it is easy to unit test.
 */
public final class MatchCalculator {

    private MatchCalculator() { }

    /**
     * @param studentLevels map of skillId -> student's level (1-5)
     * @param required      skills the job needs
     */
    public static MatchResult calculate(Map<String, Integer> studentLevels, List<RequiredSkill> required) {
        List<SkillGap> missing = new ArrayList<>();
        List<SkillGap> weak = new ArrayList<>();
        List<SkillGap> strong = new ArrayList<>();

        if (required == null || required.isEmpty()) {
            // A job with no skill requirements matches everyone.
            return new MatchResult(100.0, true, missing, weak, strong);
        }

        double weightedScore = 0;
        double totalWeight = 0;
        boolean eligible = true;

        for (RequiredSkill rs : required) {
            int requiredLevel = Math.max(1, rs.getRequiredLevel());
            int weight = Math.max(1, rs.getWeight());
            Integer lvl = studentLevels == null ? null : studentLevels.get(rs.getSkillId());
            int studentLevel = lvl == null ? 0 : Math.max(0, lvl);

            double skillScore = Math.min((double) studentLevel / requiredLevel, 1.0);
            weightedScore += skillScore * weight;
            totalWeight += weight;

            int gap = Math.max(0, requiredLevel - studentLevel);
            SkillGap row = new SkillGap(rs.getSkillId(), rs.getSkillName(), requiredLevel, studentLevel,
                    weight, rs.isMandatory(), gap, weight * gap);

            if (studentLevel == 0) {
                missing.add(row);
                if (rs.isMandatory()) {
                    eligible = false;
                }
            } else if (studentLevel < requiredLevel) {
                weak.add(row);
            } else {
                strong.add(row);
            }
        }

        double percent = Math.round(weightedScore / totalWeight * 1000.0) / 10.0;

        Comparator<SkillGap> byPriority = Comparator.comparingInt(SkillGap::priority).reversed()
                .thenComparing(SkillGap::skillName, Comparator.nullsLast(Comparator.naturalOrder()));
        missing.sort(byPriority);
        weak.sort(byPriority);
        strong.sort(Comparator.comparing(SkillGap::skillName, Comparator.nullsLast(Comparator.naturalOrder())));

        return new MatchResult(percent, eligible, missing, weak, strong);
    }
}
