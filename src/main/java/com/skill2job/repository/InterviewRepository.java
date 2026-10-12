package com.skill2job.repository;

import com.skill2job.entity.Interview;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InterviewRepository extends MongoRepository<Interview, String> {
    List<Interview> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<Interview> findByEmployerIdOrderByCreatedAtDesc(String employerId);
    List<Interview> findByApplicationId(String applicationId);
    void deleteByJobId(String jobId);
    boolean existsByStudentIdAndEmployerId(String studentId, String employerId);
    List<Interview> findByEmployerId(String employerId);
}
