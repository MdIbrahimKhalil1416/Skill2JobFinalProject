package com.skill2job.repository;

import com.skill2job.entity.CompanyReview;
import com.skill2job.enums.ReviewStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyReviewRepository extends MongoRepository<CompanyReview, String> {
    List<CompanyReview> findByEmployerIdAndStatusOrderByCreatedAtDesc(String employerId, ReviewStatus status);
    List<CompanyReview> findByStatusOrderByCreatedAtAsc(ReviewStatus status);
    List<CompanyReview> findTop50ByOrderByCreatedAtDesc();
    List<CompanyReview> findTop12ByStatusAndRatingGreaterThanEqualOrderByCreatedAtDesc(ReviewStatus status, int rating);
    List<CompanyReview> findByStudentIdOrderByCreatedAtDesc(String studentId);
    Optional<CompanyReview> findByEmployerIdAndStudentId(String employerId, String studentId);
    long countByStudentIdAndStatus(String studentId, ReviewStatus status);
    long countByStatus(ReviewStatus status);
}
