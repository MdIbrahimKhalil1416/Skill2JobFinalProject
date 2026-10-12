package com.skill2job.mock;

import com.skill2job.audit.Audited;
import com.skill2job.entity.InterviewQuestion;
import com.skill2job.entity.MockInterviewSession;
import com.skill2job.entity.Skill;
import com.skill2job.enums.AttemptStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.InterviewQuestionRepository;
import com.skill2job.repository.MockInterviewSessionRepository;
import com.skill2job.service.SkillService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mock interview practice: random open questions for 1-3 skills, the student writes answers and rates
 * themselves, then gets key-point feedback from AnswerEvaluator (rule-based - an AI reviewer could
 * be plugged in later behind the same API).
 */
@Service
public class MockInterviewService {

    private final InterviewQuestionRepository questionRepository;
    private final MockInterviewSessionRepository sessionRepository;
    private final SkillService skillService;
    private final GamificationService gamificationService;

    public MockInterviewService(InterviewQuestionRepository questionRepository,
                                MockInterviewSessionRepository sessionRepository, SkillService skillService,
                                GamificationService gamificationService) {
        this.questionRepository = questionRepository;
        this.sessionRepository = sessionRepository;
        this.skillService = skillService;
        this.gamificationService = gamificationService;
    }

    public List<MockInterviewDtos.SkillOption> skills() {
        Map<String, List<InterviewQuestion>> bySkill = questionRepository.findAll().stream()
                .collect(Collectors.groupingBy(InterviewQuestion::getSkillId));
        Map<String, Skill> skills = skillService.getMapByIds(bySkill.keySet());
        return bySkill.entrySet().stream()
                .filter(e -> skills.containsKey(e.getKey()))
                .map(e -> new MockInterviewDtos.SkillOption(e.getKey(), skills.get(e.getKey()).getName(), e.getValue().size()))
                .sorted(Comparator.comparing(MockInterviewDtos.SkillOption::skillName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public MockInterviewDtos.SessionView start(String studentId, MockInterviewDtos.StartRequest req) {
        List<String> skillIds = new ArrayList<>(new LinkedHashSet<>(req.skillIds()));
        List<InterviewQuestion> pool = questionRepository.findBySkillIdIn(skillIds);
        if (pool.isEmpty()) {
            throw ApiException.badRequest("There are no interview questions for the selected skills yet.");
        }
        // Round-robin across the chosen skills so every skill is represented.
        Map<String, List<InterviewQuestion>> bySkill = new LinkedHashMap<>();
        for (String id : skillIds) {
            List<InterviewQuestion> list = new ArrayList<>(pool.stream().filter(q -> q.getSkillId().equals(id)).toList());
            Collections.shuffle(list);
            if (!list.isEmpty()) {
                bySkill.put(id, list);
            }
        }
        List<InterviewQuestion> chosen = new ArrayList<>();
        int target = Math.min(req.count(), pool.size());
        while (chosen.size() < target) {
            boolean added = false;
            for (List<InterviewQuestion> list : bySkill.values()) {
                if (!list.isEmpty() && chosen.size() < target) {
                    chosen.add(list.remove(0));
                    added = true;
                }
            }
            if (!added) {
                break;
            }
        }

        MockInterviewSession s = new MockInterviewSession();
        s.setStudentId(studentId);
        s.setSkillNames(chosen.stream().map(InterviewQuestion::getSkillName).distinct().collect(Collectors.toCollection(ArrayList::new)));
        for (InterviewQuestion q : chosen) {
            MockInterviewSession.Item item = new MockInterviewSession.Item();
            item.setQuestionId(q.getId());
            item.setSkillName(q.getSkillName());
            item.setQuestion(q.getQuestion());
            item.setKeyPoints(new ArrayList<>(q.getKeyPoints()));
            s.getItems().add(item);
        }
        s.setStatus(AttemptStatus.IN_PROGRESS);
        s.setStartedAt(Instant.now());
        return toView(sessionRepository.save(s));
    }

    public MockInterviewDtos.SessionView submit(String studentId, String sessionId, MockInterviewDtos.SubmitRequest req) {
        MockInterviewSession s = own(studentId, sessionId);
        if (s.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw ApiException.badRequest("This practice session is already finished.");
        }
        if (req.answers().size() != s.getItems().size()) {
            throw ApiException.badRequest("Send one answer per question (" + s.getItems().size() + ").");
        }
        double coverageSum = 0;
        double ratingSum = 0;
        String weakest = null;
        double weakestCoverage = Double.MAX_VALUE;
        for (int i = 0; i < s.getItems().size(); i++) {
            MockInterviewSession.Item item = s.getItems().get(i);
            MockInterviewDtos.AnswerRequest a = req.answers().get(i);
            String text = a.answer() == null ? "" : a.answer().trim();
            AnswerEvaluator.Evaluation ev = AnswerEvaluator.evaluate(text, item.getKeyPoints());
            item.setAnswer(text);
            item.setSelfRating(a.selfRating());
            item.setCoveredPoints(new ArrayList<>(ev.covered()));
            item.setMissedPoints(new ArrayList<>(ev.missed()));
            item.setCoveragePercent(ev.coveragePercent());
            item.setFeedback(ev.feedback());
            coverageSum += ev.coveragePercent();
            ratingSum += a.selfRating();
            if (ev.coveragePercent() < weakestCoverage) {
                weakestCoverage = ev.coveragePercent();
                weakest = item.getSkillName();
            }
        }
        int n = s.getItems().size();
        double coverage = Math.round(coverageSum / n * 10.0) / 10.0;
        double rating = Math.round(ratingSum / n * 10.0) / 10.0;
        s.setCoveragePercent(coverage);
        s.setAverageSelfRating(rating);
        s.setScore(AnswerEvaluator.sessionScore(coverage, rating));
        s.setSummary(summary(coverage, rating, weakest));
        s.setStatus(AttemptStatus.SUBMITTED);
        s.setCompletedAt(Instant.now());
        sessionRepository.save(s);

        gamificationService.award(studentId, 15, "Finished your first mock interview", "MOCK_FIRST");
        gamificationService.award(studentId, 10, "Mock interview practice", "MOCK:" + GamificationService.today());
        return toView(s);
    }

    static String summary(double coverage, double rating, String weakestSkill) {
        StringBuilder sb = new StringBuilder();
        if (coverage >= 70) {
            sb.append("Interview-ready on these topics: your answers covered most key points.");
        } else if (coverage >= 40) {
            sb.append("Solid base - a bit more depth will make your answers convincing.");
        } else {
            sb.append("Keep practising: review the key points below and try again tomorrow.");
        }
        if (rating >= 4 && coverage < 50) {
            sb.append(" You rated yourself highly but missed several key points - compare your answers with them.");
        } else if (rating <= 2 && coverage >= 60) {
            sb.append(" You know more than you think - your answers were better than your self-rating.");
        }
        if (weakestSkill != null && coverage < 90) {
            sb.append(" Focus next on ").append(weakestSkill).append('.');
        }
        return sb.toString();
    }

    public MockInterviewDtos.SessionView get(String studentId, String sessionId) {
        return toView(own(studentId, sessionId));
    }

    public List<MockInterviewDtos.SessionSummary> history(String studentId) {
        return sessionRepository.findTop20ByStudentIdOrderByStartedAtDesc(studentId).stream()
                .map(s -> new MockInterviewDtos.SessionSummary(s.getId(), List.copyOf(s.getSkillNames()), s.getStatus().name(),
                        s.getItems().size(), s.getScore(), s.getStartedAt(), s.getCompletedAt()))
                .toList();
    }

    private MockInterviewSession own(String studentId, String sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getStudentId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("Practice session not found."));
    }

    private static MockInterviewDtos.SessionView toView(MockInterviewSession s) {
        boolean done = s.getStatus() == AttemptStatus.SUBMITTED;
        List<MockInterviewDtos.ItemView> items = s.getItems().stream()
                .map(i -> new MockInterviewDtos.ItemView(i.getQuestionId(), i.getSkillName(), i.getQuestion(),
                        i.getAnswer(), i.getSelfRating(),
                        done ? List.copyOf(i.getKeyPoints()) : List.of(),
                        done ? List.copyOf(i.getCoveredPoints()) : List.of(),
                        done ? List.copyOf(i.getMissedPoints()) : List.of(),
                        i.getCoveragePercent(), done ? i.getFeedback() : null))
                .toList();
        return new MockInterviewDtos.SessionView(s.getId(), List.copyOf(s.getSkillNames()), s.getStatus().name(), items,
                s.getCoveragePercent(), s.getAverageSelfRating(), s.getScore(), s.getSummary(),
                s.getStartedAt(), s.getCompletedAt());
    }

    // ------------------------------------------------------------------ admin

    public List<MockInterviewDtos.QuestionAdminView> listForAdmin(String skillId) {
        List<InterviewQuestion> list = skillId == null || skillId.isBlank()
                ? questionRepository.findAll() : questionRepository.findBySkillId(skillId);
        return list.stream()
                .sorted(Comparator.comparing(InterviewQuestion::getSkillName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(MockInterviewService::toAdmin).toList();
    }

    @Audited(action = "INTERVIEW_QUESTION_CREATE", targetType = "INTERVIEW_QUESTION", detailArg = 0)
    public MockInterviewDtos.QuestionAdminView create(MockInterviewDtos.QuestionRequest req) {
        InterviewQuestion q = new InterviewQuestion();
        q.setCreatedAt(Instant.now());
        apply(q, req);
        return toAdmin(questionRepository.save(q));
    }

    @Audited(action = "INTERVIEW_QUESTION_UPDATE", targetType = "INTERVIEW_QUESTION", targetArg = 0)
    public MockInterviewDtos.QuestionAdminView update(String id, MockInterviewDtos.QuestionRequest req) {
        InterviewQuestion q = questionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Question not found."));
        apply(q, req);
        return toAdmin(questionRepository.save(q));
    }

    @Audited(action = "INTERVIEW_QUESTION_DELETE", targetType = "INTERVIEW_QUESTION", targetArg = 0)
    public void delete(String id) {
        if (!questionRepository.existsById(id)) {
            throw ApiException.notFound("Question not found.");
        }
        questionRepository.deleteById(id);
    }

    private void apply(InterviewQuestion q, MockInterviewDtos.QuestionRequest req) {
        Skill skill = skillService.getEntity(req.skillId());
        q.setSkillId(skill.getId());
        q.setSkillName(skill.getName());
        q.setQuestion(req.question().trim());
        q.setKeyPoints(req.keyPoints().stream().map(String::trim).collect(Collectors.toCollection(ArrayList::new)));
        q.setDifficulty(req.difficulty());
    }

    private static MockInterviewDtos.QuestionAdminView toAdmin(InterviewQuestion q) {
        return new MockInterviewDtos.QuestionAdminView(q.getId(), q.getSkillId(), q.getSkillName(), q.getQuestion(),
                List.copyOf(q.getKeyPoints()), q.getDifficulty());
    }
}
