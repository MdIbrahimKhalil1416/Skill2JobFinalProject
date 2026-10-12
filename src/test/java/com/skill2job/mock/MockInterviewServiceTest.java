package com.skill2job.mock;

import com.skill2job.entity.InterviewQuestion;
import com.skill2job.entity.MockInterviewSession;
import com.skill2job.entity.Skill;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.InterviewQuestionRepository;
import com.skill2job.repository.MockInterviewSessionRepository;
import com.skill2job.service.MapRepoAccess;
import com.skill2job.service.SkillService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Mock interviews: question selection, grading on submit, hiding of key points, XP, ownership. */
class MockInterviewServiceTest {

    final Map<String, Object> qStore = new LinkedHashMap<>(), sStore = new LinkedHashMap<>();
    final InterviewQuestionRepository questions = MapRepoAccess.of(InterviewQuestionRepository.class, qStore);
    final MockInterviewSessionRepository sessions = MapRepoAccess.of(MockInterviewSessionRepository.class, sStore);
    final List<String> awards = new ArrayList<>();

    final MockInterviewService svc = new MockInterviewService(
            questions, sessions,
            new SkillService(null, null, null, null) {
                @Override public Map<String, Skill> getMapByIds(java.util.Collection<String> ids) {
                    Map<String, Skill> m = new LinkedHashMap<>();
                    for (String id : ids) m.put(id, getEntity(id));
                    return m;
                }
                @Override public Skill getEntity(String id) { Skill s = new Skill(); s.setId(id); s.setName(id.toUpperCase()); return s; }
            },
            new GamificationService(null, null, null, null, null, null) {
                @Override public void award(String id, int xp, String reason, String key) { awards.add(key + "=" + xp); }
            });

    private void bank(String skill, int n) {
        for (int i = 0; i < n; i++) {
            InterviewQuestion q = new InterviewQuestion();
            q.setId(skill + i);
            q.setSkillId(skill);
            q.setSkillName(skill.toUpperCase());
            q.setQuestion("Explain " + skill + " concept " + i);
            q.setKeyPoints(new ArrayList<>(List.of("encapsulation", "inheritance", "polymorphism")));
            q.setDifficulty(2);
            qStore.put(q.getId(), q);
        }
    }

    private static MockInterviewDtos.StartRequest start(int count, String... skills) {
        return new MockInterviewDtos.StartRequest(List.of(skills), count);
    }

    private static MockInterviewDtos.SubmitRequest answers(int n, String text, int rating) {
        List<MockInterviewDtos.AnswerRequest> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new MockInterviewDtos.AnswerRequest(text, rating));
        return new MockInterviewDtos.SubmitRequest(l);
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void startRoundRobinsAcrossTheChosenSkills() {
        bank("java", 5);
        bank("sql", 5);
        var view = svc.start("s1", start(4, "java", "sql"));
        assertEquals(4, view.items().size());
        Map<String, Long> per = view.items().stream().collect(Collectors.groupingBy(i -> i.skillName(), Collectors.counting()));
        assertEquals(2L, per.get("JAVA").longValue());
        assertEquals(2L, per.get("SQL").longValue());
        assertEquals("IN_PROGRESS", view.status());
    }

    @Test
    void startAsksForFewerQuestionsWhenTheBankIsSmallAndNeverRepeatsOne() {
        bank("java", 3);
        var view = svc.start("s1", start(8, "java"));
        assertEquals(3, view.items().size());
        assertEquals(3, view.items().stream().map(i -> i.questionId()).distinct().count());
    }

    @Test
    void startWithNoQuestionsForTheSkillIs400AndCreatesNothing() {
        bank("java", 3);
        assertEquals(400, status(() -> svc.start("s1", start(3, "ghost"))));
        assertTrue(sStore.isEmpty());
    }

    @Test
    void duplicateSkillIdsDoNotDoubleTheQuestions() {
        bank("java", 5);
        var view = svc.start("s1", start(5, "java", "java"));
        assertEquals(5, view.items().stream().map(i -> i.questionId()).distinct().count());
    }

    @Test
    void keyPointsAndFeedbackStayHiddenUntilSubmitted() {
        bank("java", 3);
        var view = svc.start("s1", start(3, "java"));
        assertTrue(view.items().stream().allMatch(i -> i.keyPoints().isEmpty() && i.feedback() == null));
        assertTrue(svc.get("s1", view.id()).items().stream().allMatch(i -> i.keyPoints().isEmpty()));
    }

    @Test
    void goodAnswersScoreHighAndEmptyAnswersScoreZero() {
        bank("java", 3);
        var good = svc.start("s1", start(3, "java"));
        var done = svc.submit("s1", good.id(), answers(3, "Encapsulation hides state, inheritance reuses code and polymorphism lets many types share one interface.", 4));
        assertEquals("SUBMITTED", done.status());
        assertTrue(done.coveragePercent() >= 90, "all three key points are mentioned: " + done.coveragePercent());
        assertTrue(done.items().stream().noneMatch(i -> i.keyPoints().isEmpty()), "key points are revealed after submit");
        assertNotNull(done.summary());

        var bad = svc.start("s1", start(3, "java"));
        var zero = svc.submit("s1", bad.id(), answers(3, "", 5));
        assertEquals(0.0, zero.coveragePercent());
        assertTrue(zero.score() < done.score());
        assertTrue(zero.summary().contains("rated yourself highly"), "high self-rating with no coverage is called out");
    }

    @Test
    void nullAnswerTextCountsAsEmptyWithoutCrashing() {
        bank("java", 3);
        var s = svc.start("s1", start(3, "java"));
        assertDoesNotThrow(() -> svc.submit("s1", s.id(), answers(3, null, 3)));
    }

    @Test
    void submitNeedsExactlyOneAnswerPerQuestion() {
        bank("java", 4);
        var s = svc.start("s1", start(4, "java"));
        assertEquals(400, status(() -> svc.submit("s1", s.id(), answers(3, "x", 3))));
        assertEquals(400, status(() -> svc.submit("s1", s.id(), answers(5, "x", 3))));
        assertEquals("IN_PROGRESS", svc.get("s1", s.id()).status(), "a rejected submit keeps the session open");
    }

    @Test
    void aSessionCanBeSubmittedOnlyOnce() {
        bank("java", 3);
        var s = svc.start("s1", start(3, "java"));
        svc.submit("s1", s.id(), answers(3, "inheritance", 3));
        assertEquals(400, status(() -> svc.submit("s1", s.id(), answers(3, "polymorphism", 3))));
        assertEquals(2, awards.size(), "XP was granted once, not twice");
    }

    @Test
    void anotherStudentCannotReadOrSubmitMySession() {
        bank("java", 3);
        var s = svc.start("s1", start(3, "java"));
        assertEquals(404, status(() -> svc.get("s2", s.id())));
        assertEquals(404, status(() -> svc.submit("s2", s.id(), answers(3, "x", 3))));
        assertEquals(404, status(() -> svc.get("s1", "missing")));
    }

    @Test
    void submitAwardsFirstTimeAndDailyXpWithOnceKeys() {
        bank("java", 3);
        var s = svc.start("s1", start(3, "java"));
        svc.submit("s1", s.id(), answers(3, "inheritance", 3));
        assertEquals(List.of("MOCK_FIRST=15", "MOCK:" + GamificationService.today() + "=10"), awards);
    }

    @Test
    void historyListsOnlyMyOwnSessions() {
        bank("java", 3);
        svc.start("s1", start(3, "java"));
        svc.start("s2", start(3, "java"));
        assertEquals(1, svc.history("s1").size());
        assertEquals(3, svc.history("s1").get(0).questions());
    }

    @Test
    void skillsListIsSortedHasCountsAndSkipsUnknownSkills() {
        bank("sql", 2);
        bank("java", 3);
        var list = svc.skills();
        assertEquals(List.of("JAVA", "SQL"), list.stream().map(MockInterviewDtos.SkillOption::skillName).toList());
        assertEquals(3, list.get(0).questionCount());
    }

    @Test
    void adminQuestionCrudTrimsValidatesAndDeletes() {
        var created = svc.create(new MockInterviewDtos.QuestionRequest("java", "  What is OOP?  ", List.of(" abstraction ", "class"), 2));
        assertEquals("What is OOP?", created.question());
        assertEquals(List.of("abstraction", "class"), created.keyPoints());
        assertEquals("JAVA", created.skillName());
        var updated = svc.update(created.id(), new MockInterviewDtos.QuestionRequest("sql", "Joins?", List.of("inner"), 3));
        assertEquals("SQL", updated.skillName());
        assertEquals(1, svc.listForAdmin(null).size());
        assertEquals(0, svc.listForAdmin("java").size());
        assertEquals(404, status(() -> svc.update("missing", new MockInterviewDtos.QuestionRequest("sql", "q", List.of("a"), 1))));
        svc.delete(created.id());
        assertEquals(404, status(() -> svc.delete(created.id())));
    }

    @Test
    void summaryWordingFollowsTheCoverageBands() {
        assertTrue(MockInterviewService.summary(80, 3, "Java").startsWith("Interview-ready"));
        assertTrue(MockInterviewService.summary(50, 3, "Java").startsWith("Solid base"));
        assertTrue(MockInterviewService.summary(10, 3, "Java").startsWith("Keep practising"));
        assertTrue(MockInterviewService.summary(70, 1, "Java").contains("know more than you think"));
        assertTrue(MockInterviewService.summary(70, 3, "Java").contains("Focus next on Java"));
        assertFalse(MockInterviewService.summary(95, 3, "Java").contains("Focus next"), "no weakest-skill hint when nearly perfect");
    }
}
