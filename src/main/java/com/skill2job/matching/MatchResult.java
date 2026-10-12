package com.skill2job.matching;

import java.util.List;

/** Result of comparing one student with one job. */
public record MatchResult(
        double matchPercent,
        boolean eligible,
        List<SkillGap> missing,
        List<SkillGap> weak,
        List<SkillGap> strong
) {
}
