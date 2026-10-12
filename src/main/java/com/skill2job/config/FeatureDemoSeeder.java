package com.skill2job.config;

import com.skill2job.entity.CompanyReview;
import com.skill2job.entity.CvEducation;
import com.skill2job.entity.CvProject;
import com.skill2job.entity.JobAlert;
import com.skill2job.entity.QuizAttempt;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.entity.User;
import com.skill2job.entity.XpEvent;
import com.skill2job.enums.AttemptStatus;
import com.skill2job.enums.ExperienceType;
import com.skill2job.enums.ReviewStatus;
import com.skill2job.repository.CompanyReviewRepository;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.JobAlertRepository;
import com.skill2job.repository.QuizAttemptRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.repository.XpEventRepository;
import com.skill2job.gamification.GamificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Demo content for the newer features (company reviews, XP/streaks, verified skills, a job alert and
 * CV-builder sections) attached to the existing demo accounts. Runs once: it only acts when the
 * company_reviews collection is still empty AND the demo users exist - so it also works on a
 * database that was seeded before these features existed, and never touches real users' data.
 */
@Component
public class FeatureDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(FeatureDemoSeeder.class);
    private static final String DEMO_KEY = "DEMO_SEED";

    private final UserRepository userRepository;
    private final StudentProfileRepository profileRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final CompanyReviewRepository reviewRepository;
    private final QuizAttemptRepository attemptRepository;
    private final JobAlertRepository alertRepository;
    private final XpEventRepository xpEventRepository;

    public FeatureDemoSeeder(UserRepository userRepository, StudentProfileRepository profileRepository,
                             EmployerProfileRepository employerProfileRepository, CompanyReviewRepository reviewRepository,
                             QuizAttemptRepository attemptRepository, JobAlertRepository alertRepository,
                             XpEventRepository xpEventRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.reviewRepository = reviewRepository;
        this.attemptRepository = attemptRepository;
        this.alertRepository = alertRepository;
        this.xpEventRepository = xpEventRepository;
    }

    public void seedIfNeeded() {
        Optional<User> s1 = user("student1@demo.com");
        Optional<User> s2 = user("student2@demo.com");
        Optional<User> s3 = user("student3@demo.com");
        Optional<User> s4 = user("student4@demo.com");
        Optional<User> s5 = user("student5@demo.com");
        Optional<User> e1 = user("employer1@demo.com");
        Optional<User> e2 = user("employer2@demo.com");
        if (reviewRepository.count() > 0 || s1.isEmpty() || e1.isEmpty() || e2.isEmpty()) {
            return;
        }

        review(s1.get(), e1.get(), 5, "Friendly, well-organised interview",
                "Clear job description, the interviewers explained the tech stack and gave feedback the same week.",
                "Only one round was online; I would have liked to see the office.", ExperienceType.INTERVIEW,
                "Junior Java Backend Developer", false, ReviewStatus.APPROVED);
        s2.ifPresent(u -> review(u, e2.get(), 4, "Great mentorship for juniors",
                "Seniors review every pull request and there is a proper onboarding plan.",
                "Deadlines can be tight before releases.", ExperienceType.JOB, "Frontend Developer (React)", false,
                ReviewStatus.APPROVED));
        s5.ifPresent(u -> review(u, e1.get(), 4, "Quick and respectful response",
                "Got an answer within days, with a polite note on what to improve.", null, ExperienceType.APPLICATION,
                "Junior Java Backend Developer", true, ReviewStatus.APPROVED));
        s3.ifPresent(u -> review(u, e2.get(), 4, "Good interview, slow scheduling",
                "Interview questions were practical and fair.", "Took a while to confirm the interview slot.",
                ExperienceType.INTERVIEW, "Frontend Developer (React)", false, ReviewStatus.PENDING));

        LocalDate today = GamificationService.today();
        s1.ifPresent(u -> progress(u, 240, today, 4, "Java", 4, 80.0));
        s2.ifPresent(u -> progress(u, 150, today, 2, "CSS", 4, 80.0));
        s3.ifPresent(u -> progress(u, 70, today.minusDays(1), 1, null, 0, 0));
        s4.ifPresent(u -> progress(u, 360, today, 6, "Java", 5, 100.0));

        s1.ifPresent(u -> {
            JobAlert a = new JobAlert();
            a.setStudentId(u.getId());
            a.setName("Java jobs in Dhaka");
            a.setKeyword("Java");
            a.setLocation("Dhaka");
            a.setMinMatch(50);
            a.setEmailEnabled(true);
            a.setActive(true);
            a.setCreatedAt(Instant.now());
            a.setLastCheckedAt(Instant.now());
            alertRepository.save(a);

            profileRepository.findByUserId(u.getId()).ifPresent(p -> {
                p.setHeadline("Aspiring Java backend developer");
                p.setCity("Dhaka");
                CvProject project = new CvProject();
                project.setTitle("Library Management REST API");
                project.setTechStack("Java, Spring Boot, MongoDB");
                project.setDescription("CRUD API with JWT login, validation and paginated search; deployed with Docker.");
                project.setLink("https://github.com/demo-rahim");
                p.setProjects(new ArrayList<>(List.of(project)));
                CvEducation edu = new CvEducation();
                edu.setDegree("B.Sc. in Computer Science & Engineering");
                edu.setInstitution("Demo University");
                edu.setStartYear("2023");
                edu.setEndYear("2027");
                edu.setResult("CGPA 3.62/4.00");
                p.setEducation(new ArrayList<>(List.of(edu)));
                p.setLanguages(new ArrayList<>(List.of("Bangla", "English")));
                p.setCvTemplate("modern");
                profileRepository.save(p);
            });
        });
        log.info("Demo data for reviews, XP, verified skills, job alerts and the CV builder added.");
    }

    private Optional<User> user(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    private void review(User student, User employer, int rating, String title, String pros, String cons,
                        ExperienceType type, String jobTitle, boolean anonymous, ReviewStatus status) {
        CompanyReview r = new CompanyReview();
        r.setEmployerId(employer.getId());
        r.setCompanyName(employerProfileRepository.findByUserId(employer.getId()).map(p -> p.getCompanyName()).orElse(employer.getName()));
        r.setStudentId(student.getId());
        r.setStudentName(student.getName());
        r.setAnonymous(anonymous);
        r.setRating(rating);
        r.setTitle(title);
        r.setPros(pros);
        r.setCons(cons);
        r.setExperienceType(type);
        r.setJobTitle(jobTitle);
        r.setStatus(status);
        r.setCreatedAt(Instant.now().minus(3, ChronoUnit.DAYS));
        r.setUpdatedAt(r.getCreatedAt());
        if (status != ReviewStatus.PENDING) {
            r.setModeratedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        }
        reviewRepository.save(r);
    }

    /** XP, a learning streak ending on lastDay, and optionally one quiz-verified skill. */
    private void progress(User u, int xp, LocalDate lastDay, int streakDays, String verifiedSkill, int level, double score) {
        profileRepository.findByUserId(u.getId()).ifPresent(p -> {
            if (p.getAwardedKeys().contains(DEMO_KEY)) {
                return;
            }
            p.setXp(p.getXp() + xp);
            p.getAwardedKeys().add(DEMO_KEY);
            for (int i = streakDays - 1; i >= 0; i--) {
                String d = lastDay.minusDays(i).toString();
                if (!p.getActivityDays().contains(d)) {
                    p.getActivityDays().add(d);
                }
            }
            p.getActivityDays().sort(String::compareTo);
            if (verifiedSkill != null) {
                for (StudentSkill s : p.getSkills()) {
                    if (s.getSkillName().equalsIgnoreCase(verifiedSkill)) {
                        s.setVerified(true);
                        s.setVerifiedLevel(level);
                        s.setVerifiedScore(score);
                        s.setVerifiedAt(Instant.now().minus(1, ChronoUnit.DAYS));
                        s.setLevel(Math.max(s.getLevel(), level));
                        QuizAttempt a = new QuizAttempt();
                        a.setStudentId(u.getId());
                        a.setSkillId(s.getSkillId());
                        a.setSkillName(s.getSkillName());
                        a.setStatus(AttemptStatus.SUBMITTED);
                        a.setTotal(5);
                        a.setCorrectCount((int) Math.round(score / 20.0));
                        a.setScorePercent(score);
                        a.setLevelAchieved(level);
                        a.setPassed(true);
                        a.setStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));
                        a.setExpiresAt(a.getStartedAt().plus(10, ChronoUnit.MINUTES));
                        a.setSubmittedAt(a.getStartedAt().plus(4, ChronoUnit.MINUTES));
                        attemptRepository.save(a);
                    }
                }
            }
            profileRepository.save(p);
            xpEventRepository.save(new XpEvent(u.getId(), xp, "Demo progress (skill tests, courses and practice)", Instant.now()));
        });
    }
}
