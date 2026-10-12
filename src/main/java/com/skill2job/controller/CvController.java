package com.skill2job.controller;

import com.skill2job.dto.StudentProfileResponse;
import com.skill2job.security.AuthUser;
import com.skill2job.service.CvService;
import com.skill2job.service.DownloadHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload, replace, delete and download for CVs. Every endpoint here requires authentication
 * (covered by the default ".requestMatchers("/api/**").authenticated()" rule in SecurityConfig);
 * CvService then decides, per request, whether this particular caller is allowed to see (or change)
 * this particular student's file.
 */
@RestController
@RequestMapping("/api/cv")
@Tag(name = "CV / Resume")
public class CvController {

    private final CvService cvService;

    public CvController(CvService cvService) {
        this.cvService = cvService;
    }

    @GetMapping("/me")
    @Operation(summary = "Download my own CV")
    public ResponseEntity<Resource> myCv(@AuthenticationPrincipal AuthUser user) {
        return download(cvService.get(user, user.id()));
    }

    @PostMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Upload or replace my CV (PDF/DOC/DOCX); replacing deletes the old file")
    public StudentProfileResponse uploadMyCv(@AuthenticationPrincipal AuthUser user,
                                             @RequestPart("cv") MultipartFile cv) {
        return cvService.upload(user, cv);
    }

    @DeleteMapping("/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Delete my CV")
    public StudentProfileResponse deleteMyCv(@AuthenticationPrincipal AuthUser user) {
        return cvService.remove(user);
    }

    @GetMapping("/{studentUserId}")
    @Operation(summary = "Download a student's CV (allowed for: the student, an admin, "
            + "or an employer the student applied to)")
    public ResponseEntity<Resource> studentCv(@AuthenticationPrincipal AuthUser user,
                                              @PathVariable String studentUserId) {
        return download(cvService.get(user, studentUserId));
    }

    private ResponseEntity<Resource> download(CvService.CvFile file) {
        Resource resource = new FileSystemResource(file.path());
        String filename = (file.originalFilename() == null || file.originalFilename().isBlank())
                ? file.path().getFileName().toString() : file.originalFilename();
        // A CV is private: never cached by a shared proxy, never content-sniffed into something executable.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, DownloadHeaders.attachment(filename))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(resource);
    }
}
