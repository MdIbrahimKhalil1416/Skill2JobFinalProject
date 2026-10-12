package com.skill2job.controller;

import com.skill2job.dto.ApplicationResponse;
import com.skill2job.dto.JobAnalysisResponse;
import com.skill2job.dto.JobRequest;
import com.skill2job.dto.JobResponse;
import com.skill2job.dto.PageResponse;
import com.skill2job.enums.JobType;
import com.skill2job.security.AuthUser;
import com.skill2job.service.ApplicationService;
import com.skill2job.service.JobService;
import com.skill2job.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs")
public class JobController {

    private final JobService jobService;
    private final MatchingService matchingService;
    private final ApplicationService applicationService;

    public JobController(JobService jobService, MatchingService matchingService,
                         ApplicationService applicationService) {
        this.jobService = jobService;
        this.matchingService = matchingService;
        this.applicationService = applicationService;
    }

    @GetMapping
    @Operation(summary = "Search open jobs (public). sort = newest | deadline")
    public PageResponse<JobResponse> search(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) JobType type,
                                            @RequestParam(required = false) String location,
                                            @RequestParam(required = false) String skillId,
                                            @RequestParam(defaultValue = "newest") String sort,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return jobService.search(keyword, type, location, skillId, sort, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Job details (public)")
    public JobResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return jobService.get(id, user);        // user is null for anonymous visitors
    }

    @GetMapping("/compare")
    @Operation(summary = "Look up 2-4 jobs at once for a side-by-side comparison (public)")
    public List<JobResponse> compare(@AuthenticationPrincipal AuthUser user, @RequestParam List<String> ids) {
        return jobService.getMany(ids, user);
    }

    @PostMapping
    @PreAuthorize("hasRole('EMPLOYER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a new job (employer)")
    public JobResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody JobRequest request) {
        return jobService.create(user, request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Edit my job (employer)")
    public JobResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                              @Valid @RequestBody JobRequest request) {
        return jobService.update(user, id, request);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Close my job (employer)")
    public JobResponse close(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return jobService.close(user, id);
    }

    @PatchMapping("/{id}/reopen")
    @PreAuthorize("hasRole('EMPLOYER')")
    @Operation(summary = "Reopen my job if the deadline has not passed (employer)")
    public JobResponse reopen(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return jobService.reopen(user, id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a job (owner or admin)")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        jobService.delete(user, id);
    }

    @GetMapping("/{id}/match")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "My match %, skill-gap report and course suggestions for this job (student)")
    public JobAnalysisResponse match(@AuthenticationPrincipal AuthUser user, @PathVariable String id) {
        return matchingService.analyze(user.id(), id);
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply to this job (student). force=true to apply when a mandatory skill is missing")
    public ApplicationResponse apply(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                     @RequestParam(defaultValue = "false") boolean force) {
        return applicationService.apply(user, id, force);
    }

    @GetMapping("/{id}/applications")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN')")
    @Operation(summary = "Applicants of my job, best match first (owner or admin). "
            + "Optional filters: minMatch (match % threshold), department, minCgpa")
    public List<ApplicationResponse> applications(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                                   @RequestParam(required = false) Double minMatch,
                                                   @RequestParam(required = false) String department,
                                                   @RequestParam(required = false) Double minCgpa) {
        return applicationService.forJob(user, id, minMatch, department, minCgpa);
    }
}
