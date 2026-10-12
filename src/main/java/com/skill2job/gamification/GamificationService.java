package com.skill2job.gamification;

import com.skill2job.entity.CourseProgress;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.entity.XpEvent;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.AttemptStatus;
import com.skill2job.enums.CourseProgressStatus;
import com.skill2job.enums.ReviewStatus;
import com.skill2job.repository.CompanyReviewRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.repository.MockInterviewSessionRepository;
import com.skill2job.repository.QuizAttemptRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.XpEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * XP, learning streak, profile completeness, badges and the leaderboard. Other services call
 * award(...) when a student does something worth rewarding; everything else is computed on read.
 * Awarding is best-effort: it never makes the action that triggered it fail.
 */
@Service
public class GamificationService {

    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);
    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");
    private static final int MAX_ACTIVITY_DAYS = 120;

    private final StudentProfileRepository profileRepository;
    private final XpEventRepository xpEventRepository;
    private final JobApplicationRepository applicationRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final MockInterviewSessionRepository mockRepository;
    private final CompanyReviewRepository reviewRepository;

    public GamificationService(StudentProfileRepository profileRepository, XpEventRepository xpEventRepository,
                               JobApplicationRepository applicationRepository, QuizAttemptRepository quizAttemptRepository,
                               MockInterviewSessionRepository mockRepository, CompanyReviewRepository reviewRepository) {
        this.profileRepository = profileRepository;
        this.xpEventRepository = xpEventRepository;
        this.applicationRepository = applicationRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.mockRepository = mockRepository;
        this.reviewRepository = reviewRepository;
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /**
     * Records learning activity for today and grants XP. With a non-null onceKey, the XP is granted
     * only the first time that key is seen (re-adding a deleted skill, re-completing a course, ...),
     * but today still counts towards the streak.
     */
    public void award(String studentUserId, int xp, String reason, String onceKey) {
        try {
            StudentProfile p = profileRepository.findByUserId(studentUserId).orElse(null);
            if (p == null) {
                return;
            }
            String day = today().toString();
            if (!p.getActivityDays().contains(day)) {
                p.getActivityDays().add(day);
                p.getActivityDays().sort(String::compareTo);
                while (p.getActivityDays().size() > MAX_ACTIVITY_DAYS) {
                    p.getActivityDays().remove(0);
                }
            }
            boolean grant = xp > 0 && (onceKey == null || !p.getAwardedKeys().contains(onceKey));
            if (grant) {
                p.setXp(p.getXp() + xp);
                if (onceKey != null) {
                    p.getAwardedKeys().add(onceKey);
                }
            }
            profileRepository.save(p);
            if (grant) {
                xpEventRepository.save(new XpEvent(studentUserId, xp, reason, Instant.now()));
            }
        } catch (RuntimeException e) {
            log.warn("Could not award XP to {} for {}: {}", studentUserId, reason, e.getMessage());
        }
    }

    /** Everything the achievements page shows. */
    public record Summary(int xp, int level, int levelProgress, int xpToNextLevel, int currentStreak, int longestStreak,
                          List<String> activityDays, int completeness, List<GamificationRules.CheckItem> checklist,
                          List<GamificationRules.Badge> badges, int badgesEarned, List<XpEventView> recentXp) { }

    public record XpEventView(int xp, String reason, Instant at) { }

    public Summary summary(StudentProfile p) {
        GamificationRules.Stats stats = stats(p);
        List<GamificationRules.CheckItem> checklist = GamificationRules.checklist(stats);
        int completeness = GamificationRules.completenessPercent(checklist);
        List<GamificationRules.Badge> badges = GamificationRules.badges(stats, completeness);
        int xp = p.getXp();
        int nextLevelXp = GamificationRules.level(xp) * GamificationRules.XP_PER_LEVEL;
        List<XpEventView> recent = xpEventRepository.findTop15ByStudentIdOrderByAtDesc(p.getUserId()).stream()
                .map(e -> new XpEventView(e.getXp(), e.getReason(), e.getAt())).toList();
        LocalDate from = today().minusDays(34);
        List<String> lastDays = p.getActivityDays().stream().filter(d -> d.compareTo(from.toString()) >= 0).toList();
        return new Summary(xp, GamificationRules.level(xp), GamificationRules.levelProgressPercent(xp), nextLevelXp - xp,
                stats.currentStreak(), stats.longestStreak(), lastDays, completeness, checklist, badges,
                (int) badges.stream().filter(GamificationRules.Badge::earned).count(), recent);
    }

    /** Lightweight numbers for dashboards and the leaderboard. */
    public int completeness(StudentProfile p) {
        return GamificationRules.completenessPercent(GamificationRules.checklist(stats(p)));
    }

    public GamificationRules.Stats stats(StudentProfile p) {
        String uid = p.getUserId();
        int verified = (int) p.getSkills().stream().filter(StudentSkill::isVerified).count();
        int endorsed = (int) p.getSkills().stream().filter(StudentSkill::isEndorsed).count();
        int completed = (int) p.getCourseProgress().stream()
                .map(CourseProgress::getStatus).filter(s -> s == CourseProgressStatus.COMPLETED).count();
        boolean portfolio = notBlank(p.getGithubUrl()) || notBlank(p.getLinkedinUrl()) || notBlank(p.getWebsiteUrl());
        return new GamificationRules.Stats(
                notBlank(p.getFullName()), notBlank(p.getDepartment()), p.getCgpa() != null, p.getGraduationYear() != null,
                notBlank(p.getBio()), p.getSkills().size(), p.hasCv(), portfolio, verified, endorsed,
                !p.getProjects().isEmpty(), !p.getEducation().isEmpty(),
                (int) applicationRepository.countByStudentId(uid),
                (int) applicationRepository.countByStudentIdAndStatus(uid, ApplicationStatus.HIRED),
                (int) quizAttemptRepository.countByStudentIdAndStatusAndScorePercentGreaterThanEqual(uid, AttemptStatus.SUBMITTED, 60.0),
                (int) quizAttemptRepository.countByStudentIdAndStatusAndScorePercentGreaterThanEqual(uid, AttemptStatus.SUBMITTED, 100.0),
                completed,
                (int) mockRepository.countByStudentIdAndStatus(uid, AttemptStatus.SUBMITTED),
                (int) reviewRepository.countByStudentIdAndStatus(uid, ReviewStatus.APPROVED),
                GamificationRules.currentStreak(p.getActivityDays(), today()),
                GamificationRules.longestStreak(p.getActivityDays()),
                p.getXp());
    }

    /** One leaderboard row; only first-name-level info is shown, never email. */
    public record LeaderboardRow(int rank, String studentId, String name, String department, int xp, int level,
                                 int verifiedSkills, int currentStreak) { }

    public List<LeaderboardRow> leaderboard() {
        List<StudentProfile> top = profileRepository.findTop20ByXpGreaterThanOrderByXpDesc(0);
        List<LeaderboardRow> rows = new ArrayList<>();
        int rank = 0;
        for (StudentProfile p : top) {
            rank++;
            int verified = (int) p.getSkills().stream().filter(StudentSkill::isVerified).count();
            rows.add(new LeaderboardRow(rank, p.getUserId(), displayName(p.getFullName()), p.getDepartment(), p.getXp(),
                    GamificationRules.level(p.getXp()), verified,
                    GamificationRules.currentStreak(p.getActivityDays(), today())));
        }
        return rows;
    }

    /** "Rahim Uddin" -> "Rahim U." - enough to recognise yourself without publishing full names. */
    static String displayName(String full) {
        if (full == null || full.isBlank()) {
            return "Student";
        }
        String[] parts = full.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0];
        }
        return parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
