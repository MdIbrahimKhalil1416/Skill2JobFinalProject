package com.skill2job.scheduler;

import com.skill2job.alerts.JobAlertService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Checks every active job alert for newly published matching jobs (default: every 15 minutes). */
@Component
public class JobAlertScheduler {

    private final JobAlertService jobAlertService;

    public JobAlertScheduler(JobAlertService jobAlertService) {
        this.jobAlertService = jobAlertService;
    }

    @Scheduled(cron = "${app.alerts.cron:0 */15 * * * *}", zone = "Asia/Dhaka")
    public void run() {
        jobAlertService.runAll();
    }
}
