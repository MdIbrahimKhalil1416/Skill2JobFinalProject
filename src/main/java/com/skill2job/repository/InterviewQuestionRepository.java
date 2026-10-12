package com.skill2job.repository;

import com.skill2job.entity.InterviewQuestion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface InterviewQuestionRepository extends MongoRepository<InterviewQuestion, String> {
    List<InterviewQuestion> findBySkillIdIn(Collection<String> skillIds);
    List<InterviewQuestion> findBySkillId(String skillId);
}
