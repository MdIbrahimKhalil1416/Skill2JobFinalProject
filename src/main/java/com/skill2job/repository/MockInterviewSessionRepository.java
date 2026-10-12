package com.skill2job.repository;

import com.skill2job.entity.MockInterviewSession;
import com.skill2job.enums.AttemptStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MockInterviewSessionRepository extends MongoRepository<MockInterviewSession, String> {
    List<MockInterviewSession> findTop20ByStudentIdOrderByStartedAtDesc(String studentId);
    long countByStudentIdAndStatus(String studentId, AttemptStatus status);
}
