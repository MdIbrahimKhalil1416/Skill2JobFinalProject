package com.skill2job.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One in-app notification (the bell in the navbar).
 * Indexes: (userId, read) for the unread badge count and "mark all read"; (userId, createdAt desc) for the
 * "latest 30" list. Both are created at startup (spring.data.mongodb.auto-index-creation=true).
 */
@Document(collection = "notifications")
@CompoundIndexes({
        @CompoundIndex(name = "user_read", def = "{'userId': 1, 'read': 1}"),
        @CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
})
public class Notification {

    @Id
    private String id;

    @Indexed
    private String userId;
    private String message;
    private boolean read;
    private Instant createdAt;

    /** Same-site relative path to open when the notification is clicked (e.g. "/chat.html?applicationId=..."),
     * built on the server when the notification is created. Null for notifications created before this field
     * existed; those open the user's dashboard. See NotificationLinks. */
    private String link;

    public Notification() { }

    public Notification(String userId, String message) {
        this(userId, message, null);
    }

    public Notification(String userId, String message, String link) {
        this.userId = userId;
        this.message = message;
        this.link = link;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
}
