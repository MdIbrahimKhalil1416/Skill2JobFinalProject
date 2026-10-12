package com.skill2job.quiz;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Pure scoring rules for skill tests (unit tested in QuizRulesTest). */
public final class QuizRules {

    /** Minimum score that counts as passing and marks the skill "verified". */
    public static final double PASS_PERCENT = 60.0;

    private QuizRules() { }

    /**
     * When the student may start this skill's test again, or null if they may start it right now.
     * Student Pro skips the cooldown entirely (the unfinished-attempt resume rule still applies elsewhere).
     */
    public static Instant retakeAt(Instant lastStartedAt, int cooldownMinutes, boolean pro, Instant now) {
        if (pro || lastStartedAt == null) {
            return null;
        }
        Instant t = lastStartedAt.plus(Duration.ofMinutes(cooldownMinutes));
        return now.isBefore(t) ? t : null;
    }

    /** Score -> proven skill level (1-5). */
    public static int levelFor(double scorePercent) {
        if (scorePercent >= 90) { return 5; }
        if (scorePercent >= 75) { return 4; }
        if (scorePercent >= 60) { return 3; }
        if (scorePercent >= 40) { return 2; }
        return 1;
    }

    public static boolean passed(double scorePercent) {
        return scorePercent >= PASS_PERCENT;
    }

    /** Percent of correct answers, rounded to one decimal. Unanswered (null or -1) counts as wrong. */
    public static double scorePercent(List<Integer> answers, List<Integer> correct) {
        if (correct == null || correct.isEmpty()) {
            return 0;
        }
        return Math.round(countCorrect(answers, correct) * 1000.0 / correct.size()) / 10.0;
    }

    public static int countCorrect(List<Integer> answers, List<Integer> correct) {
        int n = 0;
        for (int i = 0; i < correct.size(); i++) {
            Integer a = answers != null && i < answers.size() ? answers.get(i) : null;
            if (a != null && a >= 0 && a.equals(correct.get(i))) {
                n++;
            }
        }
        return n;
    }
}
