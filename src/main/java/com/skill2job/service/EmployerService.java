package com.skill2job.service;

import com.skill2job.dto.EmployerProfileRequest;
import com.skill2job.dto.EmployerProfileResponse;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Job;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Service
public class EmployerService {

    private final EmployerProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ImageStorageService imageStorageService;

    public EmployerService(EmployerProfileRepository profileRepository, UserRepository userRepository,
                           JobRepository jobRepository, ImageStorageService imageStorageService) {
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.imageStorageService = imageStorageService;
    }

    public EmployerProfile getOrCreate(String userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> {
            EmployerProfile p = new EmployerProfile();
            p.setUserId(userId);
            p.setCompanyName(userRepository.findById(userId).map(u -> u.getName()).orElse("Company"));
            return profileRepository.save(p);
        });
    }

    public EmployerProfileResponse getProfile(String userId) {
        return EmployerProfileResponse.from(getOrCreate(userId));
    }

    /** Called once from JobService.create() after a job is successfully saved. Deliberately a
     * persistent counter rather than a live JobRepository count, so deleting a job later can never
     * refund the free job-posting slot it used (see EmployerProfile.jobsPostedCount). */
    public void incrementJobsPosted(EmployerProfile profile) {
        profile.setJobsPostedCount(profile.getJobsPostedCount() + 1);
        profileRepository.save(profile);
    }

    public EmployerProfileResponse updateProfile(String userId, EmployerProfileRequest req) {
        EmployerProfile p = getOrCreate(userId);
        String oldName = p.getCompanyName();
        p.setCompanyName(req.companyName().trim());
        p.setWebsite(blankToNull(req.website()));
        p.setDescription(blankToNull(req.description()));
        p.setLocation(blankToNull(req.location()));
        p.setAboutUs(blankToNull(req.aboutUs()));
        EmployerProfile saved = profileRepository.save(p);

        // Jobs store a copy of the company name; keep them in sync.
        if (!saved.getCompanyName().equals(oldName)) {
            for (Job job : jobRepository.findByEmployerIdOrderByCreatedAtDesc(userId)) {
                job.setCompanyName(saved.getCompanyName());
                jobRepository.save(job);
            }
        }
        return EmployerProfileResponse.from(saved);
    }

    /** Read-only public profile: 404 for an unknown id instead of creating a document for it. */
    public EmployerProfileResponse getPublicProfile(String userId) {
        return EmployerProfileResponse.from(profileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Company not found.")));
    }

    public EmployerProfileResponse uploadLogo(String userId, MultipartFile logo) {
        EmployerProfile p = getOrCreate(userId);
        String oldStored = p.getLogoStoredFilename();
        String newStored = imageStorageService.store(logo);   // validated + decoded; throws before touching the old logo
        p.setLogoStoredFilename(newStored);
        p.setLogoOriginalFilename(ImageStorageService.displayName(logo));
        EmployerProfile saved;
        try {
            saved = profileRepository.save(p);
        } catch (RuntimeException e) {
            imageStorageService.delete(newStored);   // the old logo is still referenced and still on disk
            throw e;
        }
        if (oldStored != null && !oldStored.equals(newStored)) {
            imageStorageService.delete(oldStored);   // only after the new reference is safely saved
        }
        return EmployerProfileResponse.from(saved);
    }

    public record LogoFile(Path path, String contentType) {
    }

    /** Public: a job listing's company logo is meant to be visible to anyone, no authorization needed. */
    public LogoFile getLogo(String employerUserId) {
        EmployerProfile p = profileRepository.findByUserId(employerUserId)
                .orElseThrow(() -> ApiException.notFound("Company not found."));
        if (!p.hasLogo()) {
            throw ApiException.notFound("This company has not uploaded a logo.");
        }
        String stored = p.getLogoStoredFilename();
        Path path = imageStorageService.find(stored).orElse(null);
        if (path == null) {
            // Stale reference (the file is gone): clear it so the UI stops showing a broken logo.
            if (stored.equals(p.getLogoStoredFilename())) {
                p.setLogoStoredFilename(null);
                p.setLogoOriginalFilename(null);
                profileRepository.save(p);
            }
            throw ApiException.notFound("This company has not uploaded a logo.");
        }
        return new LogoFile(path, imageStorageService.contentTypeOf(stored));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
