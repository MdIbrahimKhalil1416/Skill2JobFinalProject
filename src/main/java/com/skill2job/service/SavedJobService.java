package com.skill2job.service;

import com.skill2job.dto.SavedJobResponse;
import com.skill2job.entity.Job;
import com.skill2job.entity.SavedJob;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.SavedJobRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Bookmarked jobs: a student can save a job to look at or apply to later. */
@Service
public class SavedJobService {

    private final SavedJobRepository repository;
    private final JobService jobService;

    public SavedJobService(SavedJobRepository repository, JobService jobService) {
        this.repository = repository;
        this.jobService = jobService;
    }

    public List<SavedJobResponse> mine(String studentId) {
        return repository.findByStudentIdOrderBySavedAtDesc(studentId).stream().map(SavedJobResponse::from).toList();
    }

    public SavedJobResponse save(String studentId, String jobId) {
        Job job = jobService.getVisibleEntity(jobId, null);
        if (repository.existsByStudentIdAndJobId(studentId, jobId)) {
            throw ApiException.conflict("You already bookmarked this job.");
        }
        SavedJob s = new SavedJob();
        s.setStudentId(studentId);
        s.setJobId(job.getId());
        s.setJobTitle(job.getTitle());
        s.setCompanyName(job.getCompanyName());
        s.setSavedAt(Instant.now());
        try {
            return SavedJobResponse.from(repository.save(s));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("You already bookmarked this job.");
        }
    }

    public void unsave(String studentId, String jobId) {
        if (!repository.existsByStudentIdAndJobId(studentId, jobId)) {
            throw ApiException.notFound("This job is not bookmarked.");
        }
        repository.deleteByStudentIdAndJobId(studentId, jobId);
    }
}
