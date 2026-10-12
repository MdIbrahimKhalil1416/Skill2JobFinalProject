package com.skill2job.repository;

import com.skill2job.entity.JobAlert;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface JobAlertRepository extends MongoRepository<JobAlert, String> {
    List<JobAlert> findByStudentIdOrderByCreatedAtDesc(String studentId);
    List<JobAlert> findByActiveTrue();
    long countByStudentId(String studentId);
}
