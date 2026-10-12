package com.skill2job.repository;

import com.skill2job.entity.QuizAttempt;
import com.skill2job.enums.AttemptStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends MongoRepository<QuizAttempt, String> {
    List<QuizAttempt> findByStudentIdOrderByStartedAtDesc(String studentId);
    Optional<QuizAttempt> findFirstByStudentIdAndSkillIdAndStatusOrderBySubmittedAtDesc(String studentId, String skillId, AttemptStatus status);
    long countByStudentIdAndStatusAndScorePercentGreaterThanEqual(String studentId, AttemptStatus status, double scorePercent);
    long countByStudentIdAndStatus(String studentId, AttemptStatus status);
    Optional<QuizAttempt> findFirstByStudentIdAndSkillIdOrderByStartedAtDesc(String studentId, String skillId);
}
