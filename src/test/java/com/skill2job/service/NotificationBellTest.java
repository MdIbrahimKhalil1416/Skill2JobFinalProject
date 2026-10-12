package com.skill2job.service;

import com.skill2job.entity.Notification;
import com.skill2job.entity.Payment;
import com.skill2job.enums.PaymentPurpose;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The notification bell: click targets (server-built, never an open redirect), unread count, ownership. */
class NotificationBellTest {

    final Map<String, Object> store = new LinkedHashMap<>();
    final NotificationRepository repo = MapRepo.of(NotificationRepository.class, store);
    final NotificationService bell = new NotificationService(repo);

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    private String onlyId() { return store.keySet().iterator().next(); }

    @Test
    void clickingMarksReadAndReturnsTheStoredTarget() {
        bell.notify("emp1", "Sara applied", NotificationLinks.applicants("job42"));
        assertEquals(1, bell.unreadCount("emp1"));
        var opened = bell.markRead("emp1", Role.EMPLOYER, onlyId());
        assertEquals("/employer.html?applicants=job42", opened.link());
        assertTrue(opened.read());
        assertEquals(0, bell.unreadCount("emp1"), "the badge drops at once");
        assertTrue(((Notification) store.get(onlyId())).isRead(), "persisted, not just in the response");
    }

    @Test
    void notificationsWithoutATargetOpenTheRolesDashboard() {
        bell.notify("s1", "Old style note");
        assertEquals("/student.html", bell.list("s1", Role.STUDENT).get(0).link());
        Notification legacy = new Notification("e1", "created before links existed");
        legacy.setLink(null);
        repo.save(legacy);
        assertEquals("/employer.html", bell.list("e1", Role.EMPLOYER).get(0).link());
        assertEquals("/admin.html", NotificationLinks.home(Role.ADMIN));
    }

    @Test
    void onlySameSiteRelativePagesAreEverReturned() {
        for (String bad : new String[]{"https://evil.com", "//evil.com/x.html", "/\\evil.com", "javascript:alert(1)",
                "/job.html?id=1#x", "/../admin.html", "/job.html?id=<script>", "job.html", "/api/notifications", ""}) {
            assertNull(NotificationLinks.safe(bad), bad);
        }
        assertEquals("/chat.html?applicationId=a1", NotificationLinks.safe("/chat.html?applicationId=a1"));
        // A bad value that somehow reached the database still becomes the dashboard, never a redirect.
        Notification n = new Notification("s1", "x");
        n.setLink("https://evil.com/phish");
        repo.save(n);
        assertEquals("/student.html", bell.list("s1", Role.STUDENT).get(0).link());
        bell.notify("s1", "y", "//evil.com");
        Notification viaNotify = store.values().stream().map(o -> (Notification) o)
                .filter(x -> "y".equals(x.getMessage())).findFirst().orElseThrow();
        assertNull(viaNotify.getLink(), "an unsafe link is never stored by notify()");
    }

    @Test
    void idsAreUrlEncodedIntoTheLinks() {
        assertEquals("/job.html?id=a%26b%3Dc", NotificationLinks.job("a&b=c"));
        assertEquals("/job.html?id=a%26b%3Dc", NotificationLinks.safe(NotificationLinks.job("a&b=c")));
    }

    @Test
    void aUserCanNeverReadOrMarkSomeoneElsesNotification() {
        bell.notify("victim", "private", NotificationLinks.applications());
        String id = onlyId();
        assertEquals(404, status(() -> bell.markRead("attacker", Role.STUDENT, id)), "a foreign id looks missing");
        assertEquals(1, bell.unreadCount("victim"), "and stays unread");
        assertTrue(bell.list("attacker", Role.STUDENT).isEmpty());
        bell.markAllRead("attacker");
        assertEquals(1, bell.unreadCount("victim"), "mark-all only touches the caller's own notifications");
    }

    @Test
    void markAllReadClearsTheBadge() {
        bell.notify("s1", "a", NotificationLinks.interviews());
        bell.notify("s1", "b", NotificationLinks.applications());
        bell.markAllRead("s1");
        assertEquals(0, bell.unreadCount("s1"));
        assertTrue(bell.list("s1", Role.STUDENT).stream().allMatch(r -> r.read()));
    }

    @Test
    void theListIsNewestFirstAndCappedAtThirty() {
        for (int i = 0; i < 35; i++) {
            Notification n = new Notification("s1", "n" + i);
            n.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i));
            repo.save(n);
        }
        var list = bell.list("s1", Role.STUDENT);
        assertEquals(30, list.size());
        assertEquals("n34", list.get(0).message());
    }

    @Test
    void paymentNotificationsOpenTheRightPage() {
        Payment job = new Payment();
        job.setPurpose(PaymentPurpose.JOB_POST);
        job.setJobId("j9");
        assertEquals("/job.html?id=j9", PaymentService.linkFor(job));
        Payment sub = new Payment();
        sub.setPurpose(PaymentPurpose.SUBSCRIPTION);
        assertEquals("/employer.html", PaymentService.linkFor(sub));
        Payment pro = new Payment();
        pro.setPurpose(PaymentPurpose.STUDENT_PRO);
        assertEquals("/premium.html", PaymentService.linkFor(pro));
    }
}
