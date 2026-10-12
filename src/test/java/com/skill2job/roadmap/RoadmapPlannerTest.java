package com.skill2job.roadmap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadmapPlannerTest {

    private static RoadmapPlanner.CourseInfo course(String id, int level, boolean free) {
        return new RoadmapPlanner.CourseInfo(id, "Course " + id, "Site", "https://example.com/" + id, level, free);
    }

    @Test
    void picksOneCoursePerLevelStepEasiestFirst() {
        RoadmapPlanner.Gap gap = new RoadmapPlanner.Gap("java", "Java", 1, 3, true, 10);
        List<RoadmapPlanner.CourseInfo> picked = RoadmapPlanner.pickCourses(gap,
                List.of(course("hard", 5, true), course("mid", 3, true), course("easy", 2, true), course("paid2", 2, false)),
                Set.of());
        assertEquals(List.of("easy", "mid"), picked.stream().map(RoadmapPlanner.CourseInfo::id).toList());
    }

    @Test
    void neverPicksMoreThanThreeCoursesForOneSkill() {
        RoadmapPlanner.Gap gap = new RoadmapPlanner.Gap("x", "X", 0, 5, false, 5);
        List<RoadmapPlanner.CourseInfo> picked = RoadmapPlanner.pickCourses(gap,
                List.of(course("a", 1, true), course("b", 2, true), course("c", 3, true), course("d", 4, true), course("e", 5, true)),
                Set.of());
        assertEquals(RoadmapPlanner.MAX_COURSES_PER_SKILL, picked.size());
    }

    @Test
    void mandatorySkillsComeFirstAndEachSkillEndsWithACheckpoint() {
        List<RoadmapPlanner.Gap> gaps = List.of(
                new RoadmapPlanner.Gap("git", "Git", 1, 2, false, 99),
                new RoadmapPlanner.Gap("java", "Java", 2, 3, true, 5));
        RoadmapPlanner.Plan plan = RoadmapPlanner.plan(gaps,
                Map.of("git", List.of(course("g1", 2, true)), "java", List.of(course("j1", 3, true))),
                Map.of(), Set.of("java"), 10);
        assertEquals("Java", plan.steps().get(0).skillName());
        assertEquals(RoadmapPlanner.StepType.QUIZ, plan.steps().get(1).type());
        assertEquals(RoadmapPlanner.StepType.PRACTICE, plan.steps().get(3).type());
    }

    @Test
    void completedCoursesAreNotScheduledAndInProgressAreHalved() {
        List<RoadmapPlanner.Gap> gaps = List.of(new RoadmapPlanner.Gap("java", "Java", 0, 3, true, 15));
        RoadmapPlanner.Plan plan = RoadmapPlanner.plan(gaps,
                Map.of("java", List.of(course("j1", 1, true), course("j2", 2, true), course("j3", 3, true))),
                Map.of("j1", RoadmapPlanner.Status.DONE, "j2", RoadmapPlanner.Status.IN_PROGRESS), Set.of(), 8);
        assertEquals(1, plan.alreadyDone().size());
        RoadmapPlanner.Step j2 = plan.steps().stream().filter(s -> "j2".equals(s.courseId())).findFirst().orElseThrow();
        assertEquals((RoadmapPlanner.hoursFor(2) + 1) / 2, j2.hours());
    }

    @Test
    void longStepsContinueIntoTheNextWeek() {
        List<RoadmapPlanner.Gap> gaps = List.of(new RoadmapPlanner.Gap("java", "Java", 2, 3, true, 5));
        RoadmapPlanner.Plan plan = RoadmapPlanner.plan(gaps, Map.of("java", List.of(course("j3", 3, true))),
                Map.of(), Set.of("java"), 8);
        // 18h course + 1h test = 19h at 8h/week -> 3 weeks (8 + 8 + 3)
        assertEquals(19, plan.totalHours());
        assertEquals(3, plan.weeks().size());
        assertEquals(8, plan.weeks().get(0).hours());
        assertTrue(plan.weeks().get(1).items().get(0).continued());
        assertEquals(3, plan.weeks().get(2).hours());
    }

    @Test
    void skillWithoutCoursesGetsSelfStudy() {
        List<RoadmapPlanner.Gap> gaps = List.of(new RoadmapPlanner.Gap("rust", "Rust", 0, 2, false, 4));
        RoadmapPlanner.Plan plan = RoadmapPlanner.plan(gaps, Map.of(), Map.of(), Set.of(), 10);
        assertEquals(List.of("Rust"), plan.noCourseSkills());
        assertEquals(RoadmapPlanner.StepType.PRACTICE, plan.steps().get(0).type());
    }

    @Test
    void hoursPerWeekIsClamped() {
        List<RoadmapPlanner.Gap> gaps = List.of(new RoadmapPlanner.Gap("java", "Java", 2, 3, true, 5));
        RoadmapPlanner.Plan plan = RoadmapPlanner.plan(gaps, Map.of("java", List.of(course("j3", 3, true))),
                Map.of(), Set.of(), 0);
        assertTrue(plan.weeks().stream().allMatch(w -> w.hours() <= 2));
    }
}
