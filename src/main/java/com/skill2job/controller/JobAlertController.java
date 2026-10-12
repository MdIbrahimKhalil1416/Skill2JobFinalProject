package com.skill2job.controller;

import com.skill2job.alerts.JobAlertDtos;
import com.skill2job.alerts.JobAlertService;
import com.skill2job.dto.JobMatchResponse;
import com.skill2job.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/job-alerts")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Job alerts")
public class JobAlertController {

    private final JobAlertService service;

    public JobAlertController(JobAlertService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "My saved job alerts")
    public List<JobAlertDtos.AlertResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return service.mine(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a job search as an alert (max 10)")
    public JobAlertDtos.AlertResponse create(@AuthenticationPrincipal AuthUser user,
                                             @Valid @RequestBody JobAlertDtos.AlertRequest request) {
        return service.create(user.id(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit, pause or resume an alert")
    public JobAlertDtos.AlertResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                             @Valid @RequestBody JobAlertDtos.AlertRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an alert")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        service.delete(user.id(), id);
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "Open jobs matching this alert right now")
    public List<JobMatchResponse> preview(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return service.preview(user.id(), id);
    }

    @PostMapping("/run")
    @Operation(summary = "Check my alerts now instead of waiting for the scheduler")
    public JobAlertDtos.RunResult runNow(@AuthenticationPrincipal AuthUser user) {
        return service.runForStudent(user.id());
    }
}
