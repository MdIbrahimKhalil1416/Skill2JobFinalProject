package com.skill2job.matching;

import com.skill2job.entity.RequiredSkill;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchCalculatorTest {

    private static RequiredSkill req(String id, int level, int weight, boolean mandatory) {
        return new RequiredSkill(id, id.toUpperCase(), level, weight, mandatory);
    }

    @Test
    void perfectMatchIs100() {
        MatchResult r = MatchCalculator.calculate(Map.of("java", 5, "sql", 3),
                List.of(req("java", 4, 5, true), req("sql", 3, 2, false)));
        assertEquals(100.0, r.matchPercent());
        assertTrue(r.eligible());
        assertEquals(2, r.strong().size());
    }

    @Test
    void noSkillsIsZeroAndNotEligibleWhenMandatory() {
        MatchResult r = MatchCalculator.calculate(Map.of(), List.of(req("java", 4, 5, true)));
        assertEquals(0.0, r.matchPercent());
        assertFalse(r.eligible());
        assertEquals(1, r.missing().size());
    }

    @Test
    void partialLevelGivesPartialScore() {
        // Hand calculation: min(2/4, 1) * 5 / 5 * 100 = 50.0
        MatchResult r = MatchCalculator.calculate(Map.of("java", 2), List.of(req("java", 4, 5, true)));
        assertEquals(50.0, r.matchPercent());
        assertEquals(1, r.weak().size());
        assertEquals(2, r.weak().get(0).gap());
        assertEquals(10, r.weak().get(0).priority()); // weight 5 * gap 2
    }

    @Test
    void realisticExampleFromDocs() {
        // Hand calculation:
        // Java 4/4 *5 = 5, Spring 3/3 *5 = 5, SQL 3/3 *3 = 3, Git 3/2 -> 1 *2 = 2, Docker 0 *2 = 0
        // total = 15 / 17 * 100 = 88.235... -> 88.2
        List<RequiredSkill> job = List.of(req("java", 4, 5, true), req("spring", 3, 5, true),
                req("sql", 3, 3, false), req("git", 2, 2, false), req("docker", 2, 2, false));
        MatchResult r = MatchCalculator.calculate(Map.of("java", 4, "spring", 3, "sql", 3, "git", 3), job);
        assertEquals(88.2, r.matchPercent());
        assertTrue(r.eligible());
        assertEquals(1, r.missing().size());
        assertEquals("docker", r.missing().get(0).skillId());
    }

    @Test
    void missingOptionalSkillKeepsEligibility() {
        // Hand calculation: A present (weight 1), B missing (weight 4): 1 / 5 * 100 = 20.0
        MatchResult r = MatchCalculator.calculate(Map.of("a", 3),
                List.of(req("a", 3, 1, false), req("b", 3, 4, false)));
        assertEquals(20.0, r.matchPercent());
        assertTrue(r.eligible());
    }

    @Test
    void weightsChangeTheResult() {
        List<RequiredSkill> heavyA = List.of(req("a", 3, 5, false), req("b", 3, 1, false));
        List<RequiredSkill> heavyB = List.of(req("a", 3, 1, false), req("b", 3, 5, false));
        Map<String, Integer> onlyA = Map.of("a", 3);
        assertTrue(MatchCalculator.calculate(onlyA, heavyA).matchPercent()
                > MatchCalculator.calculate(onlyA, heavyB).matchPercent());
    }

    @Test
    void emptyRequirementsMatchEveryone() {
        MatchResult r = MatchCalculator.calculate(Map.of(), List.of());
        assertEquals(100.0, r.matchPercent());
        assertTrue(r.eligible());
    }

    @Test
    void roundsToOneDecimal() {
        MatchResult r = MatchCalculator.calculate(Map.of("a", 1), List.of(req("a", 3, 1, false)));
        assertEquals(33.3, r.matchPercent());
    }

    @Test
    void missingSkillsAreSortedByPriority() {
        MatchResult r = MatchCalculator.calculate(Map.of(),
                List.of(req("low", 1, 1, false), req("high", 5, 5, false)));
        assertEquals("high", r.missing().get(0).skillId());
    }
}
