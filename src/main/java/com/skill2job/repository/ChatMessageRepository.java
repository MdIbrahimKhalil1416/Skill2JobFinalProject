package com.skill2job.repository;

import com.skill2job.entity.ChatMessage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByApplicationIdOrderBySentAtAsc(String applicationId);
    void deleteByApplicationId(String applicationId);
    void deleteByApplicationIdIn(Collection<String> applicationIds);
}
