package com.skill2job.controller;

import com.skill2job.dto.EmployerProfileRequest;
import com.skill2job.dto.EmployerProfileResponse;
import com.skill2job.dto.JobResponse;
import com.skill2job.security.AuthUser;
import com.skill2job.service.DashboardService;
import com.skill2job.service.EmployerService;
import com.skill2job.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employer")
@PreAuthorize("hasRole('EMPLOYER')")
@Tag(name = "Employer")
public class EmployerController {

    private final EmployerService employerService;
    private final JobService jobService;
    private final DashboardService dashboardService;

    public EmployerController(EmployerService employerService, JobService jobService,
                              DashboardService dashboardService) {
        this.employerService = employerService;
        this.jobService = jobService;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/profile")
    @Operation(summary = "My company profile")
    public EmployerProfileResponse profile(@AuthenticationPrincipal AuthUser user) {
        return employerService.getProfile(user.id());
    }

    @PutMapping("/profile")
    @Operation(summary = "Update my company profile")
    public EmployerProfileResponse update(@AuthenticationPrincipal AuthUser user,
                                          @Valid @RequestBody EmployerProfileRequest request) {
        return employerService.updateProfile(user.id(), request);
    }

    @PostMapping(value = "/profile/logo", consumes = "multipart/form-data")
    @Operation(summary = "Upload my company logo (PNG/JPG/WEBP, max app.logo.max-size-mb)")
    public EmployerProfileResponse uploadLogo(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam("logo") MultipartFile logo) {
        return employerService.uploadLogo(user.id(), logo);
    }

    @GetMapping("/jobs")
    @Operation(summary = "All my jobs (open and closed)")
    public List<JobResponse> jobs(@AuthenticationPrincipal AuthUser user) {
        return jobService.employerJobs(user.id());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "My dashboard numbers and chart data")
    public Map<String, Object> dashboard(@AuthenticationPrincipal AuthUser user) {
        return dashboardService.employer(user.id());
    }
}
