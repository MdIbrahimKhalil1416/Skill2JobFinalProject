package com.skill2job.scheduler;

import com.skill2job.service.NotificationLinks;
import com.skill2job.entity.Job;
import com.skill2job.enums.JobStatus;
import com.skill2job.repository.JobRepository;
import com.skill2job.service.JobService;
import com.skill2job.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Closes jobs whose deadline has passed: every day at 00:05 Bangladesh time and once at startup. */
@Component
public class JobDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobDeadlineScheduler.class);

    private final JobRepository jobRepository;
    private final NotificationService notificationService;

    public JobDeadlineScheduler(JobRepository jobRepository, NotificationService notificationService) {
        this.jobRepository = jobRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Dhaka")
    public void closeExpiredJobs() {
        List<Job> expired = jobRepository.findByStatusAndDeadlineBefore(JobStatus.OPEN, JobService.today());
        for (Job job : expired) {
            job.setStatus(JobStatus.CLOSED);
            jobRepository.save(job);
            notificationService.notify(job.getEmployerId(),
                    "Your job \"" + job.getTitle() + "\" was closed automatically because its deadline passed.",
                    NotificationLinks.employerDashboard());
        }
        if (!expired.isEmpty()) {
            log.info("Closed {} expired job(s)", expired.size());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            closeExpiredJobs();
        } catch (RuntimeException e) {
            log.warn("Could not run the deadline check at startup: {}", e.getMessage());
        }
    }
}
