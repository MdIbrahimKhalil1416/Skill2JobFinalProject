package com.skill2job.controller;

import com.skill2job.dto.ChatMessageRequest;
import com.skill2job.dto.ChatMessageResponse;
import com.skill2job.security.AuthUser;
import com.skill2job.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** In-app chat scoped to one job application (employer <-> shortlisted/hired applicant). */
@RestController
@RequestMapping("/api/applications/{applicationId}/messages")
@PreAuthorize("hasAnyRole('STUDENT','EMPLOYER','ADMIN')")
@Tag(name = "Chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    @Operation(summary = "Messages for this application, oldest first")
    public List<ChatMessageResponse> list(@AuthenticationPrincipal AuthUser user, @PathVariable String applicationId) {
        return chatService.list(user, applicationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a message about this application")
    public ChatMessageResponse send(@AuthenticationPrincipal AuthUser user, @PathVariable String applicationId,
                                    @Valid @RequestBody ChatMessageRequest request) {
        return chatService.send(user, applicationId, request.text());
    }
}
