package com.skill2job.controller;

import com.skill2job.dto.EndorsementRequest;
import com.skill2job.dto.StudentProfileResponse;
import com.skill2job.security.AuthUser;
import com.skill2job.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill endorsements, kept separate from StudentController because that controller is locked to
 * "hasRole('STUDENT')" for a student's OWN profile, while an endorsement may also be given by an
 * admin, and the target is always someone ELSE's profile.
 */
@RestController
@RequestMapping("/api/students/{studentUserId}/skills/{skillId}/endorsement")
@PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
@Tag(name = "Skill endorsements")
public class SkillEndorsementController {

    private final StudentService studentService;

    public SkillEndorsementController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PutMapping
    @Operation(summary = "Endorse or un-endorse a skill on another student's profile (peer student or admin)")
    public StudentProfileResponse setEndorsement(@AuthenticationPrincipal AuthUser user,
                                                 @PathVariable String studentUserId, @PathVariable String skillId,
                                                 @Valid @RequestBody EndorsementRequest request) {
        return studentService.setEndorsement(user, studentUserId, skillId, request.endorsed());
    }
}
