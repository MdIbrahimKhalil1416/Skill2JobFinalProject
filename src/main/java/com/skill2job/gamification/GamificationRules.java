package com.skill2job.gamification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pure rules behind gamification (no Spring, no database) so they can be unit tested:
 * XP levels, the learning streak, profile completeness and badge conditions.
 */
public final class GamificationRules {

    /** XP needed per level: level 1 = 0-99 XP, level 2 = 100-199 XP, ... */
    public static final int XP_PER_LEVEL = 100;

    private GamificationRules() { }

    public static int level(int xp) {
        return 1 + Math.max(0, xp) / XP_PER_LEVEL;
    }

    /** Progress inside the current level, 0-100. */
    public static int levelProgressPercent(int xp) {
        return (Math.max(0, xp) % XP_PER_LEVEL) * 100 / XP_PER_LEVEL;
    }

    /**
     * Consecutive active days ending today - or ending yesterday, so a streak is not "lost" just
     * because today's activity has not happened yet. Days are ISO yyyy-MM-dd strings.
     */
    public static int currentStreak(Collection<String> activityDays, LocalDate today) {
        Set<LocalDate> days = parse(activityDays);
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Longest run of consecutive active days ever recorded. */
    public static int longestStreak(Collection<String> activityDays) {
        TreeSet<LocalDate> days = parse(activityDays);
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate d : days) {
            run = (prev != null && prev.plusDays(1).equals(d)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = d;
        }
        return best;
    }

    private static TreeSet<LocalDate> parse(Collection<String> activityDays) {
        TreeSet<LocalDate> days = new TreeSet<>();
        if (activityDays != null) {
            for (String s : activityDays) {
                try {
                    days.add(LocalDate.parse(s));
                } catch (RuntimeException ignored) {
                    // a malformed entry is skipped, never fatal
                }
            }
        }
        return days;
    }

    /** One line of the profile-completeness checklist. */
    public record CheckItem(String key, String label, boolean done, String link) { }

    /** Inputs for completeness and badges, gathered by GamificationService. */
    public record Stats(boolean hasName, boolean hasDepartment, boolean hasCgpa, boolean hasGraduationYear,
                        boolean hasBio, int skillCount, boolean hasCv, boolean hasPortfolioLink,
                        int verifiedSkills, int endorsedSkills, boolean hasProject, boolean hasEducation,
                        int applications, int hired, int quizzesPassed, int perfectQuizzes,
                        int coursesCompleted, int mockInterviews, int approvedReviews,
                        int currentStreak, int longestStreak, int xp) { }

    public static List<CheckItem> checklist(Stats s) {
        List<CheckItem> items = new ArrayList<>();
        items.add(new CheckItem("name", "Add your full name", s.hasName(), "/profile.html"));
        items.add(new CheckItem("department", "Add your department", s.hasDepartment(), "/profile.html"));
        items.add(new CheckItem("cgpa", "Add your CGPA", s.hasCgpa(), "/profile.html"));
        items.add(new CheckItem("gradYear", "Add your graduation year", s.hasGraduationYear(), "/profile.html"));
        items.add(new CheckItem("bio", "Write a short bio", s.hasBio(), "/profile.html"));
        items.add(new CheckItem("skills", "Add at least 3 skills", s.skillCount() >= 3, "/profile.html"));
        items.add(new CheckItem("verified", "Verify a skill with a skill test", s.verifiedSkills() >= 1, "/quiz.html"));
        items.add(new CheckItem("portfolio", "Add a GitHub, LinkedIn or website link", s.hasPortfolioLink(), "/profile.html"));
        items.add(new CheckItem("cv", "Upload or generate your CV", s.hasCv(), "/cv-builder.html"));
        items.add(new CheckItem("project", "Add a project to your CV", s.hasProject(), "/cv-builder.html"));
        items.add(new CheckItem("education", "Add your education to your CV", s.hasEducation(), "/cv-builder.html"));
        return items;
    }

    public static int completenessPercent(List<CheckItem> items) {
        if (items.isEmpty()) {
            return 0;
        }
        long done = items.stream().filter(CheckItem::done).count();
        return (int) Math.round(done * 100.0 / items.size());
    }

    /** One badge; icon is an emoji so the frontend needs no image files. */
    public record Badge(String key, String icon, String name, String description, boolean earned) { }

    public static List<Badge> badges(Stats s, int completeness) {
        List<Badge> list = new ArrayList<>();
        list.add(new Badge("first_steps", "🌱", "First Steps", "Add your first 3 skills", s.skillCount() >= 3));
        list.add(new Badge("skill_collector", "🧰", "Skill Collector", "Have 10 or more skills on your profile", s.skillCount() >= 10));
        list.add(new Badge("verified", "✅", "Verified", "Pass your first skill test", s.quizzesPassed() >= 1));
        list.add(new Badge("triple_verified", "🛡️", "Triple Verified", "Have 3 verified skills", s.verifiedSkills() >= 3));
        list.add(new Badge("perfectionist", "💯", "Perfectionist", "Score 100% on a skill test", s.perfectQuizzes() >= 1));
        list.add(new Badge("learner", "📚", "Lifelong Learner", "Complete 3 courses", s.coursesCompleted() >= 3));
        list.add(new Badge("streak_3", "🔥", "On Fire", "Reach a 3-day learning streak", s.longestStreak() >= 3));
        list.add(new Badge("streak_7", "⚡", "Unstoppable", "Reach a 7-day learning streak", s.longestStreak() >= 7));
        list.add(new Badge("interview_ready", "🎤", "Interview Ready", "Finish 3 mock interviews", s.mockInterviews() >= 3));
        list.add(new Badge("go_getter", "🚀", "Go-Getter", "Apply to 5 jobs", s.applications() >= 5));
        list.add(new Badge("hired", "🏆", "Hired!", "Get hired through Skill2Job", s.hired() >= 1));
        list.add(new Badge("endorsed", "🤝", "Trusted", "Get a skill endorsed", s.endorsedSkills() >= 1));
        list.add(new Badge("reviewer", "⭐", "Community Voice", "Have a company review approved", s.approvedReviews() >= 1));
        list.add(new Badge("complete", "🎯", "All Set", "Reach 100% profile completeness", completeness >= 100));
        list.add(new Badge("xp_500", "💎", "XP Hunter", "Earn 500 XP", s.xp() >= 500));
        return list;
    }
}
