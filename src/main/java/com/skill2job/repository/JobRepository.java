package com.skill2job.repository;

import com.skill2job.entity.Job;
import com.skill2job.enums.JobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface JobRepository extends MongoRepository<Job, String> {
    List<Job> findByEmployerIdOrderByCreatedAtDesc(String employerId);
    List<Job> findByStatus(JobStatus status);
    List<Job> findByStatusAndDeadlineBefore(JobStatus status, LocalDate date);
    long countByStatus(JobStatus status);
    /** Every job this employer has ever posted (any status) - the payment module's free-quota count. */
    long countByEmployerId(String employerId);

    @Query(value = "{ 'requiredSkills.skillId': ?0 }", exists = true)
    boolean existsBySkillId(String skillId);
}
