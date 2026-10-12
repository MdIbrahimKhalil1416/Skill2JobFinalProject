package com.skill2job.controller;

import com.skill2job.mock.MockInterviewDtos;
import com.skill2job.mock.MockInterviewService;
import com.skill2job.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mock-interviews")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Mock interviews")
public class MockInterviewController {

    private final MockInterviewService service;

    public MockInterviewController(MockInterviewService service) {
        this.service = service;
    }

    @GetMapping("/skills")
    @Operation(summary = "Skills that have interview practice questions")
    public List<MockInterviewDtos.SkillOption> skills() {
        return service.skills();
    }

    @PostMapping
    @Operation(summary = "Start a practice session (3-8 questions from 1-3 skills)")
    public MockInterviewDtos.SessionView start(@AuthenticationPrincipal AuthUser user,
                                               @Valid @RequestBody MockInterviewDtos.StartRequest request) {
        return service.start(user.id(), request);
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit answers and self-ratings; returns key-point feedback")
    public MockInterviewDtos.SessionView submit(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                                @Valid @RequestBody MockInterviewDtos.SubmitRequest request) {
        return service.submit(user.id(), id, request);
    }

    @GetMapping
    @Operation(summary = "My recent practice sessions")
    public List<MockInterviewDtos.SessionSummary> history(@AuthenticationPrincipal AuthUser user) {
        return service.history(user.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "One practice session")
    public MockInterviewDtos.SessionView get(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return service.get(user.id(), id);
    }
}
