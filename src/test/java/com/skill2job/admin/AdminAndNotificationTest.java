package com.skill2job.admin;

import com.skill2job.entity.Notification;
import com.skill2job.entity.User;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.NotificationRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.service.AdminService;
import com.skill2job.service.MapRepoAccess;
import com.skill2job.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Admin account switching and the notification bell: ownership, read state. */
class AdminAndNotificationTest {

    final Map<String, Object> userStore = new LinkedHashMap<>(), noteStore = new LinkedHashMap<>();
    final UserRepository users = MapRepoAccess.of(UserRepository.class, userStore);
    final NotificationRepository notes = MapRepoAccess.of(NotificationRepository.class, noteStore);
    final AdminService admin = new AdminService(users);
    final NotificationService bell = new NotificationService(notes);

    private User user(String id, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(id + "@x.com");
        u.setRole(role);
        u.setActive(true);
        userStore.put(id, u);
        return u;
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void adminCanDeactivateAndReactivateAStudent() {
        User s = user("s1", Role.STUDENT);
        user("adm", Role.ADMIN);
        assertFalse(admin.setActive("adm", "s1", false).active());
        assertFalse(s.isActive());
        assertTrue(admin.setActive("adm", "s1", true).active());
    }

    @Test
    void adminCannotDeactivateSelfAnotherAdminOrAnUnknownUser() {
        user("adm", Role.ADMIN);
        user("adm2", Role.ADMIN);
        assertEquals(400, status(() -> admin.setActive("adm", "adm", false)));
        assertEquals(400, status(() -> admin.setActive("adm", "adm2", false)));
        assertEquals(404, status(() -> admin.setActive("adm", "nope", false)));
        assertTrue(((User) userStore.get("adm2")).isActive());
    }

    @Test
    void notificationsAreStoredUnreadAndCounted() {
        bell.notify("u1", "hello");
        bell.notify("u1", "again");
        bell.notify("u2", "other");
        assertEquals(2, bell.unreadCount("u1"));
        assertEquals(1, bell.unreadCount("u2"));
    }

    @Test
    void markReadWorksForTheOwnerOnly() {
        bell.notify("u1", "hello");
        String id = noteStore.keySet().iterator().next();
        assertEquals(404, status(() -> bell.markRead("u2", Role.STUDENT, id)), "another user's notification looks missing");
        assertEquals(1, bell.unreadCount("u1"));
        bell.markRead("u1", Role.STUDENT, id);
        assertEquals(0, bell.unreadCount("u1"));
        assertEquals(404, status(() -> bell.markRead("u1", Role.STUDENT, "missing")));
    }

    @Test
    void markAllReadTouchesOnlyTheCallersNotifications() {
        bell.notify("u1", "a");
        bell.notify("u1", "b");
        bell.notify("u2", "c");
        bell.markAllRead("u1");
        assertEquals(0, bell.unreadCount("u1"));
        assertEquals(1, bell.unreadCount("u2"));
        assertDoesNotThrow(() -> bell.markAllRead("nobody"));
    }
}
