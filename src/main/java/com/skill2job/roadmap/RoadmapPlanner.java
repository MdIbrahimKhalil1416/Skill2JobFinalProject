package com.skill2job.roadmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure planning logic behind the learning roadmap (no Spring, unit tested in RoadmapPlannerTest).
 *
 * 1. Skills are ordered mandatory-first, then by priority (weight x gap) - the same priority the
 *    skill-gap report uses.
 * 2. For each skill, courses are picked to climb from the student's level to the required level:
 *    one course per level step at most, closest course level first, free first; then ordered from
 *    easiest to hardest.
 * 3. After a skill's courses comes a checkpoint: the skill test (if the skill has one) or a small
 *    practice project.
 * 4. Steps are packed into weeks of `hoursPerWeek` hours; a long course simply continues into the
 *    next week. Completed courses are not scheduled again; in-progress ones need half the time.
 */
public final class RoadmapPlanner {

    public static final int MAX_COURSES_PER_SKILL = 3;

    private RoadmapPlanner() { }

    public enum StepType { COURSE, QUIZ, PRACTICE }

    public enum Status { TODO, IN_PROGRESS, DONE }

    public record Gap(String skillId, String skillName, int studentLevel, int requiredLevel, boolean mandatory, int priority) { }

    public record CourseInfo(String id, String title, String platform, String url, int level, boolean free) { }

    public record Step(StepType type, String skillId, String skillName, String title, String courseId, String platform,
                       String url, boolean free, int level, int hours, Status status) { }

    public record WeekItem(Step step, int hours, boolean continued) { }

    public record Week(int number, int hours, List<WeekItem> items) { }

    public record Plan(List<Step> steps, List<Week> weeks, List<Step> alreadyDone, List<String> noCourseSkills, int totalHours) { }

    /** Rough effort estimate: easier courses are shorter. */
    public static int hoursFor(int courseLevel) {
        return 6 + 4 * Math.max(1, Math.min(5, courseLevel));
    }

    public static Plan plan(List<Gap> gaps, Map<String, List<CourseInfo>> coursesBySkill, Map<String, Status> courseStatus,
                            Set<String> skillsWithTest, int hoursPerWeek) {
        int capacity = Math.max(2, Math.min(40, hoursPerWeek));
        List<Gap> ordered = new ArrayList<>(gaps);
        ordered.sort(Comparator.comparing((Gap g) -> !g.mandatory())
                .thenComparing(Comparator.comparingInt(Gap::priority).reversed())
                .thenComparing(Gap::skillName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));

        List<Step> steps = new ArrayList<>();
        List<Step> done = new ArrayList<>();
        List<String> noCourse = new ArrayList<>();
        Set<String> usedCourses = new LinkedHashSet<>();

        for (Gap g : ordered) {
            List<CourseInfo> picked = pickCourses(g, coursesBySkill.getOrDefault(g.skillId(), List.of()), usedCourses);
            if (picked.isEmpty()) {
                noCourse.add(g.skillName());
                steps.add(new Step(StepType.PRACTICE, g.skillId(), g.skillName(),
                        "Self-study " + g.skillName() + " (official docs and tutorials) up to level " + g.requiredLevel(),
                        null, null, null, true, g.requiredLevel(), 8, Status.TODO));
            }
            for (CourseInfo c : picked) {
                usedCourses.add(c.id());
                Status st = courseStatus.getOrDefault(c.id(), Status.TODO);
                int full = hoursFor(c.level());
                Step step = new Step(StepType.COURSE, g.skillId(), g.skillName(), c.title(), c.id(), c.platform(), c.url(),
                        c.free(), c.level(), st == Status.IN_PROGRESS ? (full + 1) / 2 : full, st);
                if (st == Status.DONE) {
                    done.add(step);
                } else {
                    steps.add(step);
                }
            }
            if (skillsWithTest.contains(g.skillId())) {
                steps.add(new Step(StepType.QUIZ, g.skillId(), g.skillName(), "Take the " + g.skillName() + " skill test",
                        null, null, "/quiz.html", true, g.requiredLevel(), 1, Status.TODO));
            } else {
                steps.add(new Step(StepType.PRACTICE, g.skillId(), g.skillName(),
                        "Build a mini project that uses " + g.skillName(), null, null, null, true, g.requiredLevel(), 4, Status.TODO));
            }
        }
        List<Week> weeks = pack(steps, capacity);
        int total = steps.stream().mapToInt(Step::hours).sum();
        return new Plan(steps, weeks, done, noCourse, total);
    }

    static List<CourseInfo> pickCourses(Gap g, List<CourseInfo> candidates, Set<String> alreadyUsed) {
        List<CourseInfo> picked = new ArrayList<>();
        int from = Math.max(0, g.studentLevel());
        int to = Math.max(from + 1, g.requiredLevel());
        for (int target = from + 1; target <= to && picked.size() < MAX_COURSES_PER_SKILL; target++) {
            final int t = target;
            candidates.stream()
                    .filter(c -> !alreadyUsed.contains(c.id()) && picked.stream().noneMatch(p -> p.id().equals(c.id())))
                    .min(Comparator.comparingInt((CourseInfo c) -> Math.abs(c.level() - t))
                            .thenComparing(c -> !c.free())
                            .thenComparing(CourseInfo::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .ifPresent(picked::add);
        }
        picked.sort(Comparator.comparingInt(CourseInfo::level));
        return picked;
    }

    static List<Week> pack(List<Step> steps, int capacity) {
        List<Week> weeks = new ArrayList<>();
        List<WeekItem> current = new ArrayList<>();
        int used = 0;
        for (Step s : steps) {
            int remaining = s.hours();
            boolean continued = false;
            while (remaining > 0) {
                int take = Math.min(remaining, capacity - used);
                current.add(new WeekItem(s, take, continued));
                used += take;
                remaining -= take;
                continued = true;
                if (used >= capacity) {
                    weeks.add(new Week(weeks.size() + 1, used, current));
                    current = new ArrayList<>();
                    used = 0;
                }
            }
        }
        if (!current.isEmpty()) {
            weeks.add(new Week(weeks.size() + 1, used, current));
        }
        return weeks;
    }
}
