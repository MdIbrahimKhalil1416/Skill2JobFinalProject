package com.skill2job.service;

import com.skill2job.dto.StudentProfileResponse;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Decides who is allowed to see a student's CV and resolves it to a file: the student themselves,
 * any admin, or an employer the student has actually applied to (never employers in general, and
 * never other students) - see StudentController/EmployerController for the equivalent ownership
 * checks used elsewhere in the project (JobService.checkOwner).
 */
@Service
public class CvService {

    private final StudentService studentService;
    private final FileStorageService fileStorageService;
    private final JobApplicationRepository jobApplicationRepository;

    public CvService(StudentService studentService, FileStorageService fileStorageService,
                     JobApplicationRepository jobApplicationRepository) {
        this.studentService = studentService;
        this.fileStorageService = fileStorageService;
        this.jobApplicationRepository = jobApplicationRepository;
    }

    public record CvFile(Path path, String originalFilename) {
    }

    public CvFile get(AuthUser requester, String studentUserId) {
        if (!canAccess(requester, studentUserId)) {
            throw ApiException.forbidden("You are not allowed to view this CV.");
        }
        StudentProfile profile = studentService.findProfile(studentUserId)
                .orElseThrow(() -> ApiException.notFound("This student has not uploaded a CV."));
        if (!profile.hasCv()) {
            throw ApiException.notFound("This student has not uploaded a CV.");
        }
        String stored = profile.getCvStoredFilename();
        Path path = fileStorageService.find(stored).orElse(null);
        if (path == null) {
            // The database points at a file that no longer exists (lost disk, manual cleanup). Don't keep
            // advertising a CV nobody can open: clear the stale reference so the student can upload again.
            studentService.clearCvIfStored(studentUserId, stored);
            throw ApiException.notFound("This CV is no longer available. The student needs to upload it again.");
        }
        return new CvFile(path, profile.getCvOriginalFilename());
    }

    /**
     * Uploads a new CV for the logged-in student, or replaces the one already on file. The old file
     * (if any) is deleted from disk only after the new one is safely stored and the profile saved,
     * so a failed upload never leaves the student with no CV at all.
     */
    public StudentProfileResponse upload(AuthUser user, MultipartFile file) {
        if (user.role() != Role.STUDENT) {
            throw ApiException.forbidden("Only students can upload a CV.");
        }
        String storedName = fileStorageService.store(file);
        StudentService.CvSwapResult result;
        try {
            result = studentService.setCv(user.id(), storedName, FileStorageService.displayName(file));
        } catch (RuntimeException e) {
            fileStorageService.delete(storedName);   // the database never saw the new file; the old CV is untouched
            throw e;
        }
        String previous = result.previousStoredFilename();
        if (previous != null && !previous.isBlank() && !previous.equals(storedName)) {
            fileStorageService.delete(previous);
        }
        return result.profile();
    }

    /** Deletes the logged-in student's CV, both the database reference and the file on disk. */
    public StudentProfileResponse remove(AuthUser user) {
        if (user.role() != Role.STUDENT) {
            throw ApiException.forbidden("Only students can delete their CV.");
        }
        String stored = studentService.clearCv(user.id());
        fileStorageService.delete(stored);
        return studentService.getProfile(user.id());
    }

    private boolean canAccess(AuthUser requester, String studentUserId) {
        if (requester.role() == Role.ADMIN) {
            return true;
        }
        if (requester.role() == Role.STUDENT) {
            return requester.id().equals(studentUserId);
        }
        if (requester.role() == Role.EMPLOYER) {
            return jobApplicationRepository.existsByEmployerIdAndStudentId(requester.id(), studentUserId);
        }
        return false;
    }
}
