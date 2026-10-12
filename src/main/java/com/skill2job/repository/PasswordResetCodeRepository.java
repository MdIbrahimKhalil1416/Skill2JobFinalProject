package com.skill2job.repository;

import com.skill2job.entity.PasswordResetCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository extends MongoRepository<PasswordResetCode, String> {
    Optional<PasswordResetCode> findByUserId(String userId);
}
