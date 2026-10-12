package com.skill2job.repository;

import com.skill2job.entity.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends MongoRepository<Payment, String> {
    Optional<Payment> findByTranId(String tranId);
    List<Payment> findByEmployerIdOrderByCreatedAtDesc(String employerId);
    List<Payment> findByStudentIdOrderByCreatedAtDesc(String studentId);
}
