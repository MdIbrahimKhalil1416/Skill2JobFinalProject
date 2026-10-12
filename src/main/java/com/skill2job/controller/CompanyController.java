package com.skill2job.controller;

import com.skill2job.dto.EmployerProfileResponse;
import com.skill2job.service.EmployerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Public company branding info (logo + "about us"): visible to anyone browsing job listings,
 * unlike a CV which needs authorization (see CvController).
 */
@RestController
@RequestMapping("/api/companies")
@Tag(name = "Company branding (public)")
public class CompanyController {

    private final EmployerService employerService;

    public CompanyController(EmployerService employerService) {
        this.employerService = employerService;
    }

    @GetMapping("/{employerUserId}")
    @Operation(summary = "Public company profile (name, about us, website, whether a logo is set)")
    public EmployerProfileResponse profile(@PathVariable String employerUserId) {
        return employerService.getPublicProfile(employerUserId);
    }

    @GetMapping("/{employerUserId}/logo")
    @Operation(summary = "Company logo image (public)")
    public ResponseEntity<Resource> logo(@PathVariable String employerUserId) {
        EmployerService.LogoFile file = employerService.getLogo(employerUserId);
        Resource resource = new FileSystemResource(file.path());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
