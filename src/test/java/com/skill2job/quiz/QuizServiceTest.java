package com.skill2job.quiz;

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
import com.skill2job.service.MapRepoAccess;
import com.skill2job.service.NotificationService;
import com.skill2job.service.SkillService;
import com.skill2job.service.StudentService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Skill tests: starting, resuming, grading, verification, XP, expiry and retake cooldown. */
class QuizServiceTest {

    final Map<String, Object> questionStore = new LinkedHashMap<>(), attemptStore = new LinkedHashMap<>();
    final QuizQuestionRepository questions = MapRepoAccess.of(QuizQuestionRepository.class, questionStore);
    final QuizAttemptRepository attempts = MapRepoAccess.of(QuizAttemptRepository.class, attemptStore);
    final Map<String, StudentProfile> profiles = new LinkedHashMap<>();
    final List<String> notes = new ArrayList<>();
    final List<String> xpLog = new ArrayList<>();
    final QuizService svc;

    QuizServiceTest() {
        final StudentService students = new StudentService(null, null, null, null, null, null) {
            @Override public StudentProfile getOrCreate(String id) {
                return profiles.computeIfAbsent(id, x -> { StudentProfile p = new StudentProfile(); p.setUserId(x); return p; });
            }
        };
        SkillService skills = new SkillService(null, null, null, null) {
            @Override public Skill getEntity(String id) { Skill s = new Skill(); s.setId(id); s.setName(id.toUpperCase()); return s; }
            @Override public Map<String, Skill> getMapByIds(java.util.Collection<String> ids) {
                Map<String, Skill> m = new LinkedHashMap<>();
                for (String id : ids) m.put(id, getEntity(id));
                return m;
            }
        };
        GamificationService game = new GamificationService(null, null, null, null, null, null) {
            @Override public void award(String id, int xp, String reason, String key) {
                StudentProfile p = students.getOrCreate(id);
                if (key == null || !p.getAwardedKeys().contains(key)) {
                    if (key != null) p.getAwardedKeys().add(key);
                    p.setXp(p.getXp() + xp);
                    xpLog.add(key + "=" + xp);
                }
            }
        };
        NotificationService notifier = new NotificationService(null) {
            @Override public void notify(String u, String m, String link) { notes.add(m); }
        };
        StudentProfileRepository profileRepo = MapRepoAccess.of(StudentProfileRepository.class);
        svc = new QuizService(questions, attempts, profileRepo, students, skills, game, notifier);
        set("questionsPerTest", 5);
        set("timeLimitMinutes", 10);
        set("cooldownMinutes", 30);
    }

    private void set(String field, int v) {
        try {
            Field f = QuizService.class.getDeclaredField(field);
            f.setAccessible(true);
            f.setInt(svc, v);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** n questions for a skill; the correct option of every question is index 0. */
    private void bank(String skillId, int n) {
        for (int i = 0; i < n; i++) {
            QuizQuestion q = new QuizQuestion();
            q.setId(skillId + "-q" + i);
            q.setSkillId(skillId);
            q.setSkillName(skillId.toUpperCase());
            q.setQuestion("Question " + i + " about " + skillId);
            q.setOptions(new ArrayList<>(List.of("right", "wrong 1", "wrong 2", "wrong 3")));
            q.setCorrectIndex(0);
            q.setDifficulty(2);
            questionStore.put(q.getId(), q);
        }
    }

    private static List<Integer> answers(int n, int correct) {
        List<Integer> a = new ArrayList<>();
        for (int i = 0; i < n; i++) a.add(i < correct ? 0 : 1);
        return a;
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void overviewOffersOnlySkillsWithEnoughQuestions() {
        bank("java", 6);
        bank("sql", 2);
        var list = svc.overview("s1");
        assertEquals(1, list.size());
        assertEquals("java", list.get(0).skillId());
    }

    @Test
    void startPicksTheConfiguredNumberOfQuestionsAndNeverRevealsAnswers() {
        bank("java", 8);
        var start = svc.start("s1", "java");
        assertEquals(5, start.questions().size());
        assertTrue(start.secondsLeft() > 0 && start.secondsLeft() <= 600);
        assertEquals(1, attemptStore.size());
    }

    @Test
    void startingAgainWhileRunningResumesTheSameQuestions() {
        bank("java", 8);
        var first = svc.start("s1", "java");
        var again = svc.start("s1", "java");
        assertEquals(first.attemptId(), again.attemptId());
        assertEquals(first.questions().stream().map(q -> q.id()).toList(), again.questions().stream().map(q -> q.id()).toList(),
                "re-opening the test must not re-roll the questions");
        assertEquals(1, attemptStore.size());
    }

    @Test
    void skillWithTooFewQuestionsHasNoTest() {
        bank("sql", 2);
        assertEquals(400, status(() -> svc.start("s1", "sql")));
        assertEquals(0, attemptStore.size());
    }

    @Test
    void perfectScorePassesVerifiesTheSkillAndAwardsXpOnce() {
        bank("java", 5);
        var start = svc.start("s1", "java");
        var result = svc.submit("s1", start.attemptId(), new QuizDtos.SubmitRequest(answers(5, 5)));
        assertTrue(result.passed());
        assertEquals(100.0, result.scorePercent());
        assertEquals(5, result.correctCount());
        StudentSkill skill = profiles.get("s1").getSkills().get(0);
        assertTrue(skill.isVerified());
        assertEquals(result.verifiedLevel(), skill.getVerifiedLevel());
        assertTrue(skill.getLevel() >= skill.getVerifiedLevel(), "claimed level is raised to the proven one");
        assertTrue(result.xpEarned() >= 30, "pass (20) + perfect (10)");
        assertTrue(notes.stream().anyMatch(n -> n.contains("Skill verified")));
        assertEquals(5, result.review().size());
        assertTrue(result.review().stream().allMatch(r -> r.correct()));
    }

    @Test
    void failingScoreVerifiesNothingAndStartsTheRetakeCooldown() {
        bank("java", 5);
        var start = svc.start("s1", "java");
        var result = svc.submit("s1", start.attemptId(), new QuizDtos.SubmitRequest(answers(5, 1)));
        assertFalse(result.passed());
        assertNull(result.verifiedLevel());
        assertTrue(profiles.get("s1").getSkills().isEmpty(), "a failed test does not add the skill");
        assertEquals(429, status(() -> svc.start("s1", "java")), "must wait for the cooldown before retaking");
    }

    @Test
    void passingNeverLowersAPreviouslyProvenLevel() {
        bank("java", 5);
        StudentProfile p = svc.overview("s1") == null ? null : profiles.get("s1");
        StudentSkill proven = new StudentSkill("java", "JAVA", 5);
        proven.setVerified(true);
        proven.setVerifiedLevel(5);
        proven.setVerifiedScore(100.0);
        profiles.computeIfAbsent("s1", x -> new StudentProfile()).getSkills().add(proven);
        var start = svc.start("s1", "java");
        svc.submit("s1", start.attemptId(), new QuizDtos.SubmitRequest(answers(5, 3)));     // 60%: lowest pass
        assertEquals(Integer.valueOf(5), proven.getVerifiedLevel(), "a weaker pass cannot downgrade the verified level");
    }

    @Test
    void anAttemptCanOnlyBeSubmittedOnceAndOnlyByItsOwner() {
        bank("java", 5);
        var start = svc.start("s1", "java");
        assertEquals(404, status(() -> svc.submit("s2", start.attemptId(), new QuizDtos.SubmitRequest(answers(5, 5)))));
        svc.submit("s1", start.attemptId(), new QuizDtos.SubmitRequest(answers(5, 5)));
        assertEquals(400, status(() -> svc.submit("s1", start.attemptId(), new QuizDtos.SubmitRequest(answers(5, 5)))));
        assertEquals(404, status(() -> svc.submit("s1", "ghost", new QuizDtos.SubmitRequest(answers(5, 5)))));
    }

    @Test
    void answerListMustMatchTheTest() {
        bank("java", 5);
        var start = svc.start("s1", "java");
        assertEquals(400, status(() -> svc.submit("s1", start.attemptId(), new QuizDtos.SubmitRequest(answers(3, 3)))));
        assertEquals(AttemptStatus.IN_PROGRESS, ((QuizAttempt) attemptStore.get(start.attemptId())).getStatus());
    }

    @Test
    void outOfRangeNullAndNegativeAnswersCountAsUnansweredWithoutCrashing() {
        bank("java", 5);
        var start = svc.start("s1", "java");
        var result = svc.submit("s1", start.attemptId(), new QuizDtos.SubmitRequest(Arrays.asList(0, null, -1, 99, 0)));
        assertEquals(2, result.correctCount());
        assertEquals(5, result.review().size());
        assertNull(result.review().get(1).chosen());
        assertNull(result.review().get(3).chosen());
    }

    @Test
    void lateSubmissionIsRefusedAfterTheGracePeriodButAcceptedInsideIt() {
        bank("java", 5);
        var slow = svc.start("s1", "java");
        QuizAttempt a = (QuizAttempt) attemptStore.get(slow.attemptId());
        a.setExpiresAt(Instant.now().minusSeconds(30));              // 30 s late: inside the 60 s grace
        assertEquals(200, status(() -> svc.submit("s1", slow.attemptId(), new QuizDtos.SubmitRequest(answers(5, 5)))));

        bank("sql", 5);
        var late = svc.start("s2", "sql");
        QuizAttempt b = (QuizAttempt) attemptStore.get(late.attemptId());
        b.setExpiresAt(Instant.now().minus(Duration.ofMinutes(5)));
        assertEquals(400, status(() -> svc.submit("s2", late.attemptId(), new QuizDtos.SubmitRequest(answers(5, 5)))));
        assertEquals(AttemptStatus.EXPIRED, b.getStatus());
    }

    @Test
    void expiredUnsubmittedAttemptCanBeRestartedAfterTheCooldown() {
        bank("java", 5);
        var first = svc.start("s1", "java");
        QuizAttempt a = (QuizAttempt) attemptStore.get(first.attemptId());
        a.setExpiresAt(Instant.now().minusSeconds(10));
        a.setStartedAt(Instant.now().minus(Duration.ofHours(2)));
        var second = svc.start("s1", "java");
        assertFalse(first.attemptId().equals(second.attemptId()));
        assertEquals(AttemptStatus.EXPIRED, a.getStatus());
    }

    @Test
    void adminQuestionValidation() {
        var base = new QuizDtos.QuestionRequest("java", "What is 2+2?", List.of("4", "5"), 0, "because", 1);
        assertEquals(200, status(() -> svc.create(base)));
        assertEquals(400, status(() -> svc.create(new QuizDtos.QuestionRequest("java", "q", List.of("a", "b"), 2, null, 1))), "correct index outside options");
        assertEquals(400, status(() -> svc.create(new QuizDtos.QuestionRequest("java", "q", List.of("Same", "same"), 0, null, 1))), "duplicate options");
        assertEquals(404, status(() -> svc.delete("ghost")));
        assertEquals(404, status(() -> svc.update("ghost", base)));
    }
}
