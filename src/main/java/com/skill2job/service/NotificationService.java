package com.skill2job.service;

import com.skill2job.dto.NotificationResponse;
import com.skill2job.entity.Notification;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * In-app notifications (bell icon in the navbar). Every read or write is scoped to the caller's own user id,
 * so one user can never list, count or mark another user's notifications (a foreign id is answered with 404,
 * exactly like a missing one).
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /** A notification without a target: it opens the recipient's dashboard. */
    public void notify(String userId, String message) {
        notify(userId, message, null);
    }

    /** @param link where clicking it leads - build it with {@link NotificationLinks}; unsafe values are dropped. */
    public void notify(String userId, String message, String link) {
        repository.save(new Notification(userId, message, NotificationLinks.safe(link)));
    }

    /** The latest 30, newest first (bounded, so the bell stays fast however many notifications exist). */
    public List<NotificationResponse> list(String userId, Role role) {
        return repository.findTop30ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> NotificationResponse.from(n, role)).toList();
    }

    public long unreadCount(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    /** Marks one of the caller's own notifications as read and returns it (with the link to open). */
    public NotificationResponse markRead(String userId, Role role, String id) {
        Notification n = repository.findById(id)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Notification not found."));
        if (!n.isRead()) {
            n.setRead(true);
            n = repository.save(n);
        }
        return NotificationResponse.from(n, role);
    }

    public void markAllRead(String userId) {
        List<Notification> unread = repository.findByUserIdAndReadFalse(userId);
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(n -> n.setRead(true));
        repository.saveAll(unread);
    }
}
