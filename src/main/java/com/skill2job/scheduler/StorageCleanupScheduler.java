package com.skill2job.scheduler;

import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.service.FileStorageService;
import com.skill2job.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Daily housekeeping for uploaded files: removes leftover temp files and files that no profile references
 * any more (a crash between "file written" and "row saved", or a failed delete). A file is only touched after
 * it is older than {@code app.storage.cleanup.min-age-hours}, so an upload in progress is never at risk.
 * Stale DB references to missing files are healed on access (CvService / EmployerService), not here.
 */
@Component
public class StorageCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(StorageCleanupScheduler.class);

    private final FileStorageService cvStorage;
    private final ImageStorageService logoStorage;
    private final StudentProfileRepository studentProfiles;
    private final EmployerProfileRepository employerProfiles;
    private final boolean enabled;
    private final Duration minAge;

    public StorageCleanupScheduler(FileStorageService cvStorage, ImageStorageService logoStorage,
                                   StudentProfileRepository studentProfiles, EmployerProfileRepository employerProfiles,
                                   @Value("${app.storage.cleanup.enabled:true}") boolean enabled,
                                   @Value("${app.storage.cleanup.min-age-hours:24}") long minAgeHours) {
        this.cvStorage = cvStorage;
        this.logoStorage = logoStorage;
        this.studentProfiles = studentProfiles;
        this.employerProfiles = employerProfiles;
        this.enabled = enabled;
        this.minAge = Duration.ofHours(Math.max(1, minAgeHours));
    }

    @Scheduled(cron = "${app.storage.cleanup.cron:0 30 3 * * *}", zone = "Asia/Dhaka")
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            int cvs = cvStorage.sweep(minAge, studentProfiles::existsByCvStoredFilename);
            int logos = logoStorage.sweep(minAge, employerProfiles::existsByLogoStoredFilename);
            if (cvs + logos > 0) {
                log.info("Storage cleanup removed {} orphaned CV file(s) and {} logo file(s).", cvs, logos);
            }
        } catch (RuntimeException e) {
            log.warn("Storage cleanup failed (will retry tomorrow): {}", e.getMessage());
        }
    }
}
