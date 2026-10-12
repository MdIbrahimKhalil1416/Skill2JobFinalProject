package com.skill2job.repository;

import com.skill2job.entity.JobApplication;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends MongoRepository<JobApplication, String> {
    boolean existsByJobIdAndStudentId(String jobId, String studentId);
    Optional<JobApplication> findByJobIdAndStudentId(String jobId, String studentId);
    List<JobApplication> findByStudentIdOrderByAppliedAtDesc(String studentId);
    List<JobApplication> findByJobIdOrderByMatchPercentDesc(String jobId);
    List<JobApplication> findByEmployerId(String employerId);
    /** Used to authorize an employer to download an applicant's CV: only if they applied to one of their jobs. */
    boolean existsByEmployerIdAndStudentId(String employerId, String studentId);
    long countByJobId(String jobId);
    long countByStudentId(String studentId);
    long countByStudentIdAndStatus(String studentId, com.skill2job.enums.ApplicationStatus status);
    List<JobApplication> findByStudentIdAndEmployerId(String studentId, String employerId);
    void deleteByJobId(String jobId);
}
