package com.skill2job.controller;

import com.skill2job.dto.NotificationResponse;
import com.skill2job.security.AuthUser;
import com.skill2job.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "My latest notifications")
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthUser user) {
        return notificationService.list(user.id(), user.role());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications")
    public Map<String, Long> unread(@AuthenticationPrincipal AuthUser user) {
        return Map.of("count", notificationService.unreadCount(user.id()));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one of my notifications as read; returns it with the (server-built) link to open")
    public NotificationResponse markRead(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return notificationService.markRead(user.id(), user.role(), id);
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark all notifications as read")
    public void markAllRead(@AuthenticationPrincipal AuthUser user) {
        notificationService.markAllRead(user.id());
    }
}
