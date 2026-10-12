package com.skill2job.dto;

import com.skill2job.entity.ChatMessage;
import com.skill2job.enums.Role;

import java.time.Instant;

public record ChatMessageResponse(String id, String applicationId, String senderId, Role senderRole,
                                  String senderName, String text, Instant sentAt) {
    public static ChatMessageResponse from(ChatMessage m) {
        return new ChatMessageResponse(m.getId(), m.getApplicationId(), m.getSenderId(), m.getSenderRole(),
                m.getSenderName(), m.getText(), m.getSentAt());
    }
}
