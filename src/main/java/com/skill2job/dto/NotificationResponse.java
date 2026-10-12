package com.skill2job.dto;

import com.skill2job.entity.Notification;
import com.skill2job.enums.Role;
import com.skill2job.service.NotificationLinks;

import java.time.Instant;

/** {@code link} is always a safe same-site relative path (the stored target, or the user's dashboard). */
public record NotificationResponse(String id, String message, boolean read, Instant createdAt, String link) {
    public static NotificationResponse from(Notification n, Role role) {
        return new NotificationResponse(n.getId(), n.getMessage(), n.isRead(), n.getCreatedAt(),
                NotificationLinks.resolve(n.getLink(), role));
    }
}
