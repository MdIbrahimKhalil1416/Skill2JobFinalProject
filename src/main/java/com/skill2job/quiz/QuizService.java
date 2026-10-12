package com.skill2job.quiz;

import com.skill2job.service.NotificationLinks;
import com.skill2job.audit.Audited;
import com.skill2job.entity.QuizAttempt;
import com.skill2job.entity.QuizQuestion;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.enums.AttemptStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.QuizAttemptRepository;
import com.skill2job.repository.QuizQuestionRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.service.NotificationService;
import com.skill2job.service.SkillService;
import com.skill2job.service.StudentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Skill tests: a student takes a short multiple-choice test for a skill; passing marks the skill
 * "verified" at the level the score proves (QuizRules). The questions are chosen and stored on the
 * server when the attempt starts and the correct answers are only revealed after submitting.
 */
@Service
public class QuizService {

    /** A skill needs at least this many questions in its bank before a test is offered. */
    public static final int MIN_QUESTIONS = 3;

    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final StudentProfileRepository profileRepository;
    private final StudentService studentService;
    private final SkillService skillService;
    private final GamificationService gamificationService;
    private final NotificationService notificationService;

    @Value("${app.quiz.questions-per-test:5}")
    private int questionsPerTest;
    @Value("${app.quiz.time-limit-minutes:10}")
    private int timeLimitMinutes;
    @Value("${app.quiz.cooldown-minutes:30}")
    private int cooldownMinutes;

    public QuizService(QuizQuestionRepository questionRepository, QuizAttemptRepository attemptRepository,
                       StudentProfileRepository profileRepository, StudentService studentService,
                       SkillService skillService, GamificationService gamificationService,
                       NotificationService notificationService) {
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.profileRepository = profileRepository;
        this.studentService = studentService;
        this.skillService = skillService;
        this.gamificationService = gamificationService;
        this.notificationService = notificationService;
    }

    // ------------------------------------------------------------------ student

    /** Every skill that has a test, with this student's status for it. */
    public List<QuizDtos.SkillQuizSummary> overview(String studentId) {
        Map<String, Long> counts = questionRepository.findAll().stream()
                .collect(Collectors.groupingBy(QuizQuestion::getSkillId, Collectors.counting()));
        StudentProfile profile = studentService.getOrCreate(studentId);
        Map<String, StudentSkill> mine = profile.getSkills().stream()
                .collect(Collectors.toMap(StudentSkill::getSkillId, Function.identity(), (a, b) -> a));
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdOrderByStartedAtDesc(studentId);
        Map<String, Skill> skills = skillService.getMapByIds(counts.keySet());
        Instant now = Instant.now();

        List<QuizDtos.SkillQuizSummary> list = new ArrayList<>();
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() < MIN_QUESTIONS) {
                continue;
            }
            Skill skill = skills.get(e.getKey());
            if (skill == null) {
                continue;
            }
            StudentSkill own = mine.get(skill.getId());
            List<QuizAttempt> forSkill = attempts.stream().filter(a -> skill.getId().equals(a.getSkillId())).toList();
            Double lastScore = forSkill.stream().filter(a -> a.getStatus() == AttemptStatus.SUBMITTED)
                    .findFirst().map(QuizAttempt::getScorePercent).orElse(null);
            QuizAttempt latest = forSkill.isEmpty() ? null : forSkill.get(0);
            boolean inProgress = latest != null && latest.getStatus() == AttemptStatus.IN_PROGRESS
                    && latest.getExpiresAt() != null && now.isBefore(latest.getExpiresAt());
            Instant retakeAt = null;
            if (!inProgress && latest != null) {
                retakeAt = QuizRules.retakeAt(latest.getStartedAt(), cooldownMinutes, profile.isProActive(), now);
            }
            list.add(new QuizDtos.SkillQuizSummary(skill.getId(), skill.getName(), skill.getCategory(), e.getValue(),
                    own != null, own == null ? null : own.getLevel(), own != null && own.isVerified(),
                    own == null ? null : own.getVerifiedLevel(), own == null ? null : own.getVerifiedScore(),
                    lastScore, (int) forSkill.stream().filter(a -> a.getStatus() == AttemptStatus.SUBMITTED).count(),
                    retakeAt, inProgress));
        }
        // The student's own skills first, then alphabetical.
        list.sort(Comparator.comparing((QuizDtos.SkillQuizSummary s) -> !s.hasSkill())
                .thenComparing(QuizDtos.SkillQuizSummary::skillName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    /** Starts a test - or resumes the one still running, so re-starting can never re-roll the questions. */
    public QuizDtos.StartResponse start(String studentId, String skillId) {
        Skill skill = skillService.getEntity(skillId);
        Instant now = Instant.now();
        Optional<QuizAttempt> latest = attemptRepository.findFirstByStudentIdAndSkillIdOrderByStartedAtDesc(studentId, skillId);
        if (latest.isPresent()) {
            QuizAttempt last = latest.get();
            if (last.getStatus() == AttemptStatus.IN_PROGRESS && last.getExpiresAt() != null && now.isBefore(last.getExpiresAt())) {
                return toStart(last, loadInOrder(last.getQuestionIds()));
            }
            if (last.getStatus() == AttemptStatus.IN_PROGRESS) {
                last.setStatus(AttemptStatus.EXPIRED);
                attemptRepository.save(last);
            }
            boolean pro = studentService.getOrCreate(studentId).isProActive();
            Instant retakeAt = QuizRules.retakeAt(last.getStartedAt(), cooldownMinutes, pro, now);
            if (retakeAt != null) {
                long minutes = Math.max(1, Duration.between(now, retakeAt).toMinutes() + 1);
                throw ApiException.tooManyRequests("You can retake the " + skill.getName() + " test in about "
                        + minutes + " minute(s). Use the time to review the course suggestions!");
            }
        }

        List<QuizQuestion> bank = new ArrayList<>(questionRepository.findBySkillId(skillId));
        if (bank.size() < MIN_QUESTIONS) {
            throw ApiException.badRequest("There is no test for " + skill.getName() + " yet.");
        }
        Collections.shuffle(bank);
        List<QuizQuestion> chosen = bank.subList(0, Math.min(Math.max(MIN_QUESTIONS, questionsPerTest), bank.size()));

        QuizAttempt attempt = new QuizAttempt();
        attempt.setStudentId(studentId);
        attempt.setSkillId(skill.getId());
        attempt.setSkillName(skill.getName());
        attempt.setQuestionIds(chosen.stream().map(QuizQuestion::getId).collect(Collectors.toCollection(ArrayList::new)));
        attempt.setTotal(chosen.size());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(now);
        attempt.setExpiresAt(now.plus(Duration.ofMinutes(timeLimitMinutes)));
        attempt = attemptRepository.save(attempt);
        gamificationService.award(studentId, 5, "Took the " + skill.getName() + " skill test",
                "QUIZ_TRY:" + skill.getId() + ":" + GamificationService.today());
        return toStart(attempt, chosen);
    }

    public QuizDtos.ResultResponse submit(String studentId, String attemptId, QuizDtos.SubmitRequest req) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.getStudentId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("Test attempt not found."));
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw ApiException.badRequest("This test has already been submitted.");
        }
        // One minute of grace for slow networks.
        if (attempt.getExpiresAt() != null && Instant.now().isAfter(attempt.getExpiresAt().plusSeconds(60))) {
            attempt.setStatus(AttemptStatus.EXPIRED);
            attemptRepository.save(attempt);
            throw ApiException.badRequest("Time is up for this test. Start a new one when the retake time allows.");
        }
        List<QuizQuestion> questions = loadInOrder(attempt.getQuestionIds());
        if (req.answers().size() != questions.size()) {
            throw ApiException.badRequest("Answer list does not match the test (" + questions.size() + " questions).");
        }
        List<Integer> answers = new ArrayList<>();
        List<Integer> correct = new ArrayList<>();
        List<QuizDtos.ReviewItem> review = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            QuizQuestion q = questions.get(i);
            Integer a = req.answers().get(i);
            int chosen = a == null || a < 0 || a >= q.getOptions().size() ? -1 : a;
            answers.add(chosen);
            correct.add(q.getCorrectIndex());
            review.add(new QuizDtos.ReviewItem(q.getQuestion(), List.copyOf(q.getOptions()), chosen < 0 ? null : chosen,
                    q.getCorrectIndex(), chosen == q.getCorrectIndex(), q.getExplanation()));
        }
        double score = QuizRules.scorePercent(answers, correct);
        int level = QuizRules.levelFor(score);
        boolean passed = QuizRules.passed(score);

        attempt.setAnswers(answers);
        attempt.setCorrectCount(QuizRules.countCorrect(answers, correct));
        attempt.setScorePercent(score);
        attempt.setLevelAchieved(level);
        attempt.setPassed(passed);
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(Instant.now());
        attemptRepository.save(attempt);

        Integer verifiedLevel = applyVerification(studentId, attempt);
        int xp = 0;
        if (passed) {
            xp += awardIfNew(studentId, 20, "Passed the " + attempt.getSkillName() + " test (level " + level + ")",
                    "QUIZ_PASS:" + attempt.getSkillId() + ":" + level);
            if (score >= 100) {
                xp += awardIfNew(studentId, 10, "Perfect score in " + attempt.getSkillName(), "QUIZ_PERFECT:" + attempt.getSkillId());
            }
            notificationService.notify(studentId, "Skill verified: " + attempt.getSkillName() + " at level "
                    + verifiedLevel + " (" + score + "%).", NotificationLinks.mySkills());
        }
        return new QuizDtos.ResultResponse(attempt.getId(), attempt.getSkillId(), attempt.getSkillName(),
                attempt.getCorrectCount(), attempt.getTotal(), score, level, passed, verifiedLevel, xp, review);
    }

    public List<QuizDtos.AttemptView> history(String studentId) {
        return attemptRepository.findByStudentIdOrderByStartedAtDesc(studentId).stream()
                .limit(30)
                .map(a -> new QuizDtos.AttemptView(a.getId(), a.getSkillName(), a.getScorePercent(), a.getLevelAchieved(),
                        a.isPassed(), a.getStatus().name(), a.getStartedAt(), a.getSubmittedAt()))
                .toList();
    }

    /**
     * Passing verifies the skill at the best level ever proven, adding the skill to the profile if it
     * is not there yet. A claimed level below the proven one is raised to it. Returns the verified
     * level after this attempt (null if the skill is still unverified).
     */
    private Integer applyVerification(String studentId, QuizAttempt attempt) {
        StudentProfile p = studentService.getOrCreate(studentId);
        StudentSkill skill = p.getSkills().stream().filter(s -> s.getSkillId().equals(attempt.getSkillId()))
                .findFirst().orElse(null);
        if (!attempt.isPassed()) {
            return skill != null && skill.isVerified() ? skill.getVerifiedLevel() : null;
        }
        if (skill == null) {
            if (p.getSkills().size() >= StudentService.MAX_SKILLS) {
                return null;
            }
            skill = new StudentSkill(attempt.getSkillId(), attempt.getSkillName(), attempt.getLevelAchieved());
            p.getSkills().add(skill);
            p.getSkills().sort(Comparator.comparing(StudentSkill::getSkillName, String.CASE_INSENSITIVE_ORDER));
        }
        int best = Math.max(skill.getVerifiedLevel() == null ? 0 : skill.getVerifiedLevel(), attempt.getLevelAchieved());
        if (skill.getVerifiedLevel() == null || attempt.getLevelAchieved() >= skill.getVerifiedLevel()) {
            skill.setVerifiedScore(attempt.getScorePercent());
            skill.setVerifiedAt(Instant.now());
        }
        skill.setVerified(true);
        skill.setVerifiedLevel(best);
        if (skill.getLevel() < best) {
            skill.setLevel(best);
        }
        profileRepository.save(p);
        return best;
    }

    private int awardIfNew(String studentId, int xp, String reason, String key) {
        StudentProfile p = studentService.getOrCreate(studentId);
        boolean isNew = !p.getAwardedKeys().contains(key);
        gamificationService.award(studentId, xp, reason, key);
        return isNew ? xp : 0;
    }

    private QuizDtos.StartResponse toStart(QuizAttempt a, List<QuizQuestion> questions) {
        long secondsLeft = Math.max(0, Duration.between(Instant.now(), a.getExpiresAt()).getSeconds());
        List<QuizDtos.QuestionView> views = questions.stream()
                .map(q -> new QuizDtos.QuestionView(q.getId(), q.getQuestion(), List.copyOf(q.getOptions()), q.getDifficulty()))
                .toList();
        return new QuizDtos.StartResponse(a.getId(), a.getSkillId(), a.getSkillName(), a.getExpiresAt(), secondsLeft, views);
    }

    /** Loads questions keeping the attempt's order; a question deleted meanwhile is simply dropped. */
    private List<QuizQuestion> loadInOrder(List<String> ids) {
        Map<String, QuizQuestion> byId = new HashMap<>();
        questionRepository.findAllById(ids).forEach(q -> byId.put(q.getId(), q));
        List<QuizQuestion> list = new ArrayList<>();
        for (String id : ids) {
            QuizQuestion q = byId.get(id);
            if (q != null) {
                list.add(q);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ admin

    public List<QuizDtos.QuestionAdminView> listForAdmin(String skillId) {
        List<QuizQuestion> list = skillId == null || skillId.isBlank()
                ? questionRepository.findAll() : questionRepository.findBySkillId(skillId);
        return list.stream()
                .sorted(Comparator.comparing(QuizQuestion::getSkillName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(QuizQuestion::getQuestion, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(QuizService::toAdmin).toList();
    }

    @Audited(action = "QUIZ_QUESTION_CREATE", targetType = "QUIZ_QUESTION", detailArg = 0)
    public QuizDtos.QuestionAdminView create(QuizDtos.QuestionRequest req) {
        QuizQuestion q = new QuizQuestion();
        q.setCreatedAt(Instant.now());
        apply(q, req);
        return toAdmin(questionRepository.save(q));
    }

    @Audited(action = "QUIZ_QUESTION_UPDATE", targetType = "QUIZ_QUESTION", targetArg = 0)
    public QuizDtos.QuestionAdminView update(String id, QuizDtos.QuestionRequest req) {
        QuizQuestion q = questionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Question not found."));
        apply(q, req);
        return toAdmin(questionRepository.save(q));
    }

    @Audited(action = "QUIZ_QUESTION_DELETE", targetType = "QUIZ_QUESTION", targetArg = 0)
    public void delete(String id) {
        if (!questionRepository.existsById(id)) {
            throw ApiException.notFound("Question not found.");
        }
        questionRepository.deleteById(id);
    }

    private void apply(QuizQuestion q, QuizDtos.QuestionRequest req) {
        Skill skill = skillService.getEntity(req.skillId());
        List<String> options = req.options().stream().map(String::trim).toList();
        if (req.correctIndex() >= options.size()) {
            throw ApiException.badRequest("The correct option must be one of the options.");
        }
        if (options.stream().map(String::toLowerCase).distinct().count() != options.size()) {
            throw ApiException.badRequest("Two options are the same.");
        }
        q.setSkillId(skill.getId());
        q.setSkillName(skill.getName());
        q.setQuestion(req.question().trim());
        q.setOptions(new ArrayList<>(options));
        q.setCorrectIndex(req.correctIndex());
        q.setExplanation(req.explanation() == null || req.explanation().isBlank() ? null : req.explanation().trim());
        q.setDifficulty(req.difficulty());
    }

    private static QuizDtos.QuestionAdminView toAdmin(QuizQuestion q) {
        return new QuizDtos.QuestionAdminView(q.getId(), q.getSkillId(), q.getSkillName(), q.getQuestion(),
                List.copyOf(q.getOptions()), q.getCorrectIndex(), q.getExplanation(), q.getDifficulty());
    }
}
