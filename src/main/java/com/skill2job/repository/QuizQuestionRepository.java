package com.skill2job.repository;

import com.skill2job.entity.QuizQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface QuizQuestionRepository extends MongoRepository<QuizQuestion, String> {
    List<QuizQuestion> findBySkillId(String skillId);
    long countBySkillId(String skillId);
}
