package com.skill2job.repository;

import com.skill2job.entity.JobTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface JobTemplateRepository extends MongoRepository<JobTemplate, String> {
    List<JobTemplate> findByEmployerIdOrderByCreatedAtDesc(String employerId);
}
