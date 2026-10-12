package com.skill2job.repository;

import com.skill2job.entity.EmployerProfile;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmployerProfileRepository extends MongoRepository<EmployerProfile, String> {
    Optional<EmployerProfile> findByUserId(String userId);

    boolean existsByLogoStoredFilename(String logoStoredFilename);
}
