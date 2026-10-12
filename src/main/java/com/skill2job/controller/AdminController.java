package com.skill2job.controller;

import com.skill2job.dto.ActiveUpdateRequest;
import com.skill2job.dto.PageResponse;
import com.skill2job.dto.StudentProfileResponse;
import com.skill2job.dto.UserResponse;
import com.skill2job.enums.Role;
import com.skill2job.security.AuthUser;
import com.skill2job.service.AdminService;
import com.skill2job.service.DashboardService;
import com.skill2job.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminController {

    private final AdminService adminService;
    private final DashboardService dashboardService;
    private final StudentService studentService;

    public AdminController(AdminService adminService, DashboardService dashboardService, StudentService studentService) {
        this.adminService = adminService;
        this.dashboardService = dashboardService;
        this.studentService = studentService;
    }

    @GetMapping("/users")
    @Operation(summary = "List users, optionally by role")
    public PageResponse<UserResponse> users(@RequestParam(required = false) Role role,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return adminService.users(role, page, size);
    }

    @PatchMapping("/users/{id}/active")
    @Operation(summary = "Activate or deactivate a user")
    public UserResponse setActive(@AuthenticationPrincipal AuthUser admin, @PathVariable String id,
                                  @Valid @RequestBody ActiveUpdateRequest request) {
        return adminService.setActive(admin.id(), id, request.active());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "System-wide statistics (uses MongoDB aggregation)")
    public Map<String, Object> dashboard() {
        return dashboardService.admin();
    }

    @GetMapping("/students/{userId}/profile")
    @Operation(summary = "A student's profile and skills (for the skill endorsement screen)")
    public StudentProfileResponse studentProfile(@PathVariable String userId) {
        return studentService.getProfile(userId);
    }
}
