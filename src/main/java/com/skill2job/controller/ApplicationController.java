package com.skill2job.controller;

import com.skill2job.dto.ApplicationResponse;
import com.skill2job.dto.BulkStatusUpdateRequest;
import com.skill2job.dto.StatusUpdateRequest;
import com.skill2job.security.AuthUser;
import com.skill2job.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN')")
    @Operation(summary = "Change application status: APPLIED -> SHORTLISTED/REJECTED -> HIRED")
    public ApplicationResponse updateStatus(@AuthenticationPrincipal AuthUser user, @PathVariable String id,
                                            @Valid @RequestBody StatusUpdateRequest request) {
        return applicationService.updateStatus(user, id, request.status());
    }

    @PatchMapping("/bulk-status")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN')")
    @Operation(summary = "Bulk shortlist/reject: apply one status to several applications at once")
    public Map<String, Object> bulkUpdateStatus(@AuthenticationPrincipal AuthUser user,
                                                @Valid @RequestBody BulkStatusUpdateRequest request) {
        return applicationService.bulkUpdateStatus(user, request.applicationIds(), request.status());
    }
}
