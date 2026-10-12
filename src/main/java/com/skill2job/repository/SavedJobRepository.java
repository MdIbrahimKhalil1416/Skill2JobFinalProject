package com.skill2job.repository;

import com.skill2job.entity.SavedJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SavedJobRepository extends MongoRepository<SavedJob, String> {
    List<SavedJob> findByStudentIdOrderBySavedAtDesc(String studentId);
    boolean existsByStudentIdAndJobId(String studentId, String jobId);
    Optional<SavedJob> findByStudentIdAndJobId(String studentId, String jobId);
    void deleteByStudentIdAndJobId(String studentId, String jobId);
    void deleteByJobId(String jobId);
    List<SavedJob> findByDeadlineReminderSentFalse();
}
