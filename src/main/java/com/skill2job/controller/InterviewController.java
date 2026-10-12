package com.skill2job.controller;

import com.skill2job.dto.InterviewProposeRequest;
import com.skill2job.dto.InterviewResponse;
import com.skill2job.dto.InterviewSelectRequest;
import com.skill2job.security.AuthUser;
import com.skill2job.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@PreAuthorize("hasAnyRole('STUDENT','EMPLOYER')")
@Tag(name = "Interviews")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping
    @Operation(summary = "My interviews (proposed slots as a student, or slots I proposed as an employer)")
    public List<InterviewResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return interviewService.mine(user);
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Propose interview time slots for an application (employer)")
    public InterviewResponse propose(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody InterviewProposeRequest request) {
        return interviewService.propose(user, request.applicationId(), request.slots());
    }

    @PatchMapping("/{id}/select")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Pick one of the proposed interview slots (student)")
    public InterviewResponse select(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                    @Valid @RequestBody InterviewSelectRequest request) {
        return interviewService.selectSlot(user, id, request.slot());
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN')")
    @Operation(summary = "Cancel a proposed or scheduled interview (employer or admin)")
    public InterviewResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return interviewService.cancel(user, id);
    }
}
