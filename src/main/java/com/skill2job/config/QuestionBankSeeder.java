package com.skill2job.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skill2job.entity.InterviewQuestion;
import com.skill2job.entity.QuizQuestion;
import com.skill2job.entity.Skill;
import com.skill2job.repository.InterviewQuestionRepository;
import com.skill2job.repository.QuizQuestionRepository;
import com.skill2job.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loads the skill-test (MCQ) and mock-interview question banks from src/main/resources/data/*.json.
 *
 * Runs independently of the one-time demo seed: each bank is inserted whenever ITS OWN collection is
 * empty, so an installation that already has data (where the demo seed never runs again) still gets
 * the question banks on its next restart. Questions whose skill does not exist are skipped.
 */
@Component
public class QuestionBankSeeder {

    private static final Logger log = LoggerFactory.getLogger(QuestionBankSeeder.class);

    /** JSON shape of one MCQ. */
    public record QuizSeed(String skill, int difficulty, String question, List<String> options, int answer,
                           String explanation) { }

    /** JSON shape of one open interview question. */
    public record InterviewSeed(String skill, int difficulty, String question, List<String> keyPoints) { }

    private final QuizQuestionRepository quizRepository;
    private final InterviewQuestionRepository interviewRepository;
    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    public QuestionBankSeeder(QuizQuestionRepository quizRepository, InterviewQuestionRepository interviewRepository,
                              SkillRepository skillRepository, ObjectMapper objectMapper) {
        this.quizRepository = quizRepository;
        this.interviewRepository = interviewRepository;
        this.skillRepository = skillRepository;
        this.objectMapper = objectMapper;
    }

    public void seedIfEmpty() {
        if (quizRepository.count() == 0) {
            seedQuiz();
        }
        if (interviewRepository.count() == 0) {
            seedInterview();
        }
    }

    private void seedQuiz() {
        QuizSeed[] seeds = read("data/quiz-questions.json", QuizSeed[].class);
        if (seeds == null) {
            return;
        }
        List<QuizQuestion> list = new ArrayList<>();
        for (QuizSeed s : seeds) {
            Optional<Skill> skill = skillRepository.findByNameIgnoreCase(s.skill());
            if (skill.isEmpty() || s.options() == null || s.answer() < 0 || s.answer() >= s.options().size()) {
                continue;
            }
            QuizQuestion q = new QuizQuestion();
            q.setSkillId(skill.get().getId());
            q.setSkillName(skill.get().getName());
            q.setQuestion(s.question());
            q.setOptions(new ArrayList<>(s.options()));
            q.setCorrectIndex(s.answer());
            q.setExplanation(s.explanation());
            q.setDifficulty(Math.max(1, Math.min(5, s.difficulty())));
            q.setCreatedAt(Instant.now());
            list.add(q);
        }
        quizRepository.saveAll(list);
        log.info("Skill-test question bank loaded: {} questions", list.size());
    }

    private void seedInterview() {
        InterviewSeed[] seeds = read("data/interview-questions.json", InterviewSeed[].class);
        if (seeds == null) {
            return;
        }
        List<InterviewQuestion> list = new ArrayList<>();
        for (InterviewSeed s : seeds) {
            Optional<Skill> skill = skillRepository.findByNameIgnoreCase(s.skill());
            if (skill.isEmpty() || s.keyPoints() == null || s.keyPoints().isEmpty()) {
                continue;
            }
            InterviewQuestion q = new InterviewQuestion();
            q.setSkillId(skill.get().getId());
            q.setSkillName(skill.get().getName());
            q.setQuestion(s.question());
            q.setKeyPoints(new ArrayList<>(s.keyPoints()));
            q.setDifficulty(Math.max(1, Math.min(5, s.difficulty())));
            q.setCreatedAt(Instant.now());
            list.add(q);
        }
        interviewRepository.saveAll(list);
        log.info("Mock-interview question bank loaded: {} questions", list.size());
    }

    private <T> T read(String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException e) {
            log.error("Could not read {}: {}", path, e.getMessage());
            return null;
        }
    }
}
