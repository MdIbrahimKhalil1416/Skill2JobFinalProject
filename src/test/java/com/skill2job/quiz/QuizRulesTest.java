package com.skill2job.quiz;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuizRulesTest {

    @Test
    void scoreToLevelBoundaries() {
        assertEquals(5, QuizRules.levelFor(100));
        assertEquals(5, QuizRules.levelFor(90));
        assertEquals(4, QuizRules.levelFor(89.9));
        assertEquals(4, QuizRules.levelFor(75));
        assertEquals(3, QuizRules.levelFor(60));
        assertEquals(2, QuizRules.levelFor(59.9));
        assertEquals(2, QuizRules.levelFor(40));
        assertEquals(1, QuizRules.levelFor(0));
    }

    @Test
    void passThresholdIsSixtyPercent() {
        assertTrue(QuizRules.passed(60));
        assertFalse(QuizRules.passed(59.9));
    }

    @Test
    void unansweredCountsAsWrong() {
        List<Integer> correct = List.of(1, 2, 0, 3, 1);
        List<Integer> answers = Arrays.asList(1, -1, 0, null, 2);
        assertEquals(2, QuizRules.countCorrect(answers, correct));
        assertEquals(40.0, QuizRules.scorePercent(answers, correct), 0.001);
    }

    @Test
    void roundsToOneDecimal() {
        assertEquals(66.7, QuizRules.scorePercent(List.of(0, 0, 1), List.of(0, 0, 0)), 0.001);
    }

    @Test
    void shortAnswerListIsSafe() {
        assertEquals(20.0, QuizRules.scorePercent(List.of(1), List.of(1, 1, 1, 1, 1)), 0.001);
        assertEquals(0.0, QuizRules.scorePercent(null, List.of(1)), 0.001);
    }

    @Test
    void retakeCooldownAppliesToFreeStudentsOnly() {
        Instant start = Instant.parse("2026-10-01T10:00:00Z");
        Instant soon = start.plusSeconds(10 * 60);
        Instant later = start.plusSeconds(31 * 60);
        assertEquals(start.plusSeconds(30 * 60), QuizRules.retakeAt(start, 30, false, soon));
        assertEquals(null, QuizRules.retakeAt(start, 30, false, later), "cooldown over");
        assertEquals(null, QuizRules.retakeAt(start, 30, true, soon), "Student Pro skips the cooldown");
        assertEquals(null, QuizRules.retakeAt(null, 30, false, soon), "no earlier attempt");
    }
}
