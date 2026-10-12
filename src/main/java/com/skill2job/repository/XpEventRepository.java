package com.skill2job.repository;

import com.skill2job.entity.XpEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface XpEventRepository extends MongoRepository<XpEvent, String> {
    List<XpEvent> findTop15ByStudentIdOrderByAtDesc(String studentId);
}
