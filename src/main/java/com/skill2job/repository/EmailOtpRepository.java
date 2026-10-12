package com.skill2job.repository;

import com.skill2job.entity.EmailOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EmailOtpRepository extends MongoRepository<EmailOtp, String> {
    Optional<EmailOtp> findByUserId(String userId);
    void deleteByUserId(String userId);
}
