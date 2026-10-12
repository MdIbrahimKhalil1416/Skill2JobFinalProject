package com.skill2job.scheduler;

import com.skill2job.service.NotificationLinks;
import com.skill2job.entity.Job;
import com.skill2job.entity.SavedJob;
import com.skill2job.entity.User;
import com.skill2job.repository.SavedJobRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.service.EmailService;
import com.skill2job.service.JobService;
import com.skill2job.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * In-app + email reminder: "deadline in N day(s) for a job you bookmarked".
 * Runs once a day (and once at startup, so the demo data is immediately visible) and never
 * reminds about the same saved job twice (SavedJob.deadlineReminderSent).
 */
@Component
public class DeadlineReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderScheduler.class);
    private static final int REMIND_WITHIN_DAYS = 2;

    private final SavedJobRepository savedJobRepository;
    private final com.skill2job.repository.JobRepository jobRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final com.skill2job.repository.JobApplicationRepository applicationRepository;

    public DeadlineReminderScheduler(SavedJobRepository savedJobRepository,
                                     com.skill2job.repository.JobRepository jobRepository,
                                     UserRepository userRepository, NotificationService notificationService,
                                     EmailService emailService,
                                     com.skill2job.repository.JobApplicationRepository applicationRepository) {
        this.savedJobRepository = savedJobRepository;
        this.jobRepository = jobRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.applicationRepository = applicationRepository;
    }

    @Scheduled(cron = "0 15 0 * * *", zone = "Asia/Dhaka")
    public void sendReminders() {
        LocalDate today = JobService.today();
        int sent = 0;
        for (SavedJob saved : savedJobRepository.findByDeadlineReminderSentFalse()) {
            Job job = jobRepository.findById(saved.getJobId()).orElse(null);
            if (job == null || job.getDeadline() == null || job.getStatus() != com.skill2job.enums.JobStatus.OPEN) {
                continue;       // deleted, unpublished or closed: there is nothing left to apply to
            }
            long daysLeft = ChronoUnit.DAYS.between(today, job.getDeadline());
            if (daysLeft < 0 || daysLeft > REMIND_WITHIN_DAYS) {
                continue;
            }
            if (applicationRepository.existsByJobIdAndStudentId(job.getId(), saved.getStudentId())) {
                saved.setDeadlineReminderSent(true);      // already applied: no "apply now" nudge, and don't re-check daily
                savedJobRepository.save(saved);
                continue;
            }
            String when = daysLeft == 0 ? "today" : "in " + daysLeft + (daysLeft == 1 ? " day" : " days");
            notificationService.notify(saved.getStudentId(),
                    "Deadline is " + when + " for \"" + job.getTitle() + "\" at " + job.getCompanyName()
                            + ", a job you bookmarked.", NotificationLinks.job(job.getId()));
            userRepository.findById(saved.getStudentId()).ifPresent((User u) ->
                    emailService.sendDeadlineReminderEmail(u.getEmail(), u.getName(), job.getTitle(),
                            job.getCompanyName(), job.getDeadline()));
            saved.setDeadlineReminderSent(true);
            savedJobRepository.save(saved);
            sent++;
        }
        if (sent > 0) {
            log.info("Sent {} deadline reminder(s) for bookmarked jobs", sent);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            sendReminders();
        } catch (RuntimeException e) {
            log.warn("Could not run the deadline reminder check at startup: {}", e.getMessage());
        }
    }
}
