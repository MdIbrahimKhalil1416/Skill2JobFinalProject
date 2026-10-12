package com.skill2job.controller;

import com.skill2job.dto.JobTemplateRequest;
import com.skill2job.dto.JobTemplateResponse;
import com.skill2job.security.AuthUser;
import com.skill2job.service.JobTemplateService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employer/job-templates")
@PreAuthorize("hasRole('EMPLOYER')")
@Tag(name = "Job templates")
public class JobTemplateController {

    private final JobTemplateService jobTemplateService;

    public JobTemplateController(JobTemplateService jobTemplateService) {
        this.jobTemplateService = jobTemplateService;
    }

    @GetMapping
    @Operation(summary = "My saved job templates")
    public List<JobTemplateResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return jobTemplateService.mine(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a reusable job template (title, type, location, required skills)")
    public JobTemplateResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody JobTemplateRequest request) {
        return jobTemplateService.create(user.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete one of my job templates")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        jobTemplateService.delete(user.id(), id);
    }
}
