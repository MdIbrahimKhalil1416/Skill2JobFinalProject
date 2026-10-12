package com.skill2job.gamification;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GamificationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Test
    void levelsAreHundredXpEach() {
        assertEquals(1, GamificationRules.level(0));
        assertEquals(1, GamificationRules.level(99));
        assertEquals(2, GamificationRules.level(100));
        assertEquals(6, GamificationRules.level(512));
        assertEquals(12, GamificationRules.levelProgressPercent(512));
    }

    @Test
    void streakCountsBackFromToday() {
        assertEquals(3, GamificationRules.currentStreak(List.of("2026-10-06", "2026-10-07", "2026-10-08"), TODAY));
    }

    @Test
    void streakSurvivesUntilEndOfTodayIfYesterdayWasActive() {
        assertEquals(2, GamificationRules.currentStreak(List.of("2026-10-06", "2026-10-07"), TODAY));
    }

    @Test
    void streakBreaksOnAGap() {
        assertEquals(0, GamificationRules.currentStreak(List.of("2026-10-05", "2026-10-06"), TODAY));
        assertEquals(1, GamificationRules.currentStreak(List.of("2026-10-05", "2026-10-08"), TODAY));
    }

    @Test
    void longestStreakFindsBestRun() {
        assertEquals(4, GamificationRules.longestStreak(List.of(
                "2026-09-01", "2026-09-02", "2026-09-03", "2026-09-04", "2026-09-10", "2026-09-11", "garbage")));
    }

    @Test
    void completenessAndBadges() {
        GamificationRules.Stats empty = new GamificationRules.Stats(false, false, false, false, false, 0, false, false,
                0, 0, false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        List<GamificationRules.CheckItem> items = GamificationRules.checklist(empty);
        assertEquals(0, GamificationRules.completenessPercent(items));
        assertTrue(GamificationRules.badges(empty, 0).stream().noneMatch(GamificationRules.Badge::earned));

        GamificationRules.Stats full = new GamificationRules.Stats(true, true, true, true, true, 12, true, true,
                3, 1, true, true, 6, 1, 3, 1, 3, 3, 1, 7, 7, 600);
        int pct = GamificationRules.completenessPercent(GamificationRules.checklist(full));
        assertEquals(100, pct);
        assertTrue(GamificationRules.badges(full, pct).stream().allMatch(GamificationRules.Badge::earned));
    }

    @Test
    void displayNameHidesSurname() {
        assertEquals("Rahim U.", GamificationService.displayName("Rahim Uddin"));
        assertEquals("Nusrat", GamificationService.displayName("Nusrat"));
        assertEquals("Student", GamificationService.displayName("  "));
    }
}
