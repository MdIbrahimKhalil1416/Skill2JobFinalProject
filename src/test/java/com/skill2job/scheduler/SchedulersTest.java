package com.skill2job.scheduler;

import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.entity.SavedJob;
import com.skill2job.entity.User;
import com.skill2job.enums.JobStatus;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.SavedJobRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.service.EmailService;
import com.skill2job.service.JobService;
import com.skill2job.service.MapRepoAccess;
import com.skill2job.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The daily jobs: auto-closing expired jobs and bookmark deadline reminders. */
class SchedulersTest {

    final Map<String, Object> jobStore = new LinkedHashMap<>(), savedStore = new LinkedHashMap<>(),
            userStore = new LinkedHashMap<>(), appStore = new LinkedHashMap<>();
    final JobRepository jobs = MapRepoAccess.of(JobRepository.class, jobStore);
    final SavedJobRepository saved = MapRepoAccess.of(SavedJobRepository.class, savedStore);
    final UserRepository users = MapRepoAccess.of(UserRepository.class, userStore);
    final JobApplicationRepository apps = MapRepoAccess.of(JobApplicationRepository.class, appStore);
    final List<String> notes = new ArrayList<>();
    final List<String> emails = new ArrayList<>();
    final NotificationService notifier = new NotificationService(null) {
        @Override public void notify(String userId, String message, String link) { notes.add(userId + ": " + message); }
    };
    final EmailService mail = new EmailService(null) {
        @Override public boolean sendDeadlineReminderEmail(String to, String n, String t, String c, LocalDate d) { emails.add(to); return true; }
    };

    private Job job(JobStatus st, int deadlineInDays) {
        Job j = new Job();
        j.setEmployerId("emp1");
        j.setTitle("Job " + deadlineInDays);
        j.setCompanyName("Co");
        j.setStatus(st);
        j.setDeadline(JobService.today().plusDays(deadlineInDays));
        return jobs.save(j);
    }

    private SavedJob bookmark(Job j, String student) {
        SavedJob s = new SavedJob();
        s.setJobId(j.getId());
        s.setStudentId(student);
        return saved.save(s);
    }

    private DeadlineReminderScheduler reminders() {
        User u = new User();
        u.setId("s1");
        u.setEmail("s1@uni.edu");
        u.setName("S1");
        userStore.put("s1", u);
        return new DeadlineReminderScheduler(saved, jobs, users, notifier, mail, apps);
    }

    // ------------------------------------------------------------------ auto-close

    @Test
    void onlyOpenJobsPastTheirDeadlineAreClosedAndTheEmployerIsTold() {
        Job expired = job(JobStatus.OPEN, -1);
        Job dueToday = job(JobStatus.OPEN, 0);
        Job future = job(JobStatus.OPEN, 5);
        Job alreadyClosed = job(JobStatus.CLOSED, -3);
        Job unpaid = job(JobStatus.PENDING_PAYMENT, -2);
        new JobDeadlineScheduler(jobs, notifier).closeExpiredJobs();
        assertEquals(JobStatus.CLOSED, jobs.findById(expired.getId()).get().getStatus());
        assertEquals(JobStatus.OPEN, jobs.findById(dueToday.getId()).get().getStatus(), "the deadline day itself is still open");
        assertEquals(JobStatus.OPEN, jobs.findById(future.getId()).get().getStatus());
        assertEquals(JobStatus.CLOSED, jobs.findById(alreadyClosed.getId()).get().getStatus());
        assertEquals(JobStatus.PENDING_PAYMENT, jobs.findById(unpaid.getId()).get().getStatus());
        assertEquals(1, notes.size());
        assertTrue(notes.get(0).startsWith("emp1:") && notes.get(0).contains("closed automatically"));
    }

    @Test
    void runningTheCloserTwiceDoesNotNotifyTwice() {
        job(JobStatus.OPEN, -1);
        JobDeadlineScheduler s = new JobDeadlineScheduler(jobs, notifier);
        s.closeExpiredJobs();
        s.closeExpiredJobs();
        assertEquals(1, notes.size());
    }

    // ------------------------------------------------------------------ bookmark reminders

    @Test
    void remindsOnlyWithinTwoDaysAndOnlyOnce() {
        Job soon = job(JobStatus.OPEN, 2);
        Job later = job(JobStatus.OPEN, 3);
        Job today = job(JobStatus.OPEN, 0);
        bookmark(soon, "s1");
        bookmark(later, "s1");
        bookmark(today, "s1");
        DeadlineReminderScheduler r = reminders();
        r.sendReminders();
        assertEquals(2, notes.size());
        assertTrue(notes.stream().anyMatch(n -> n.contains("in 2 days")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("today")));
        assertEquals(2, emails.size());
        r.sendReminders();
        assertEquals(2, notes.size(), "never twice for the same bookmark");
    }

    @Test
    void oneDayWordingIsSingular() {
        bookmark(job(JobStatus.OPEN, 1), "s1");
        reminders().sendReminders();
        assertTrue(notes.get(0).contains("in 1 day for"));
    }

    /** REGRESSION: the reminder used to go out for closed jobs ("Apply now if you are still interested"). */
    @Test
    void noReminderForClosedUnpaidOrDeletedJobs() {
        bookmark(job(JobStatus.CLOSED, 1), "s1");
        bookmark(job(JobStatus.PENDING_PAYMENT, 1), "s1");
        SavedJob orphan = new SavedJob();
        orphan.setJobId("deleted-job");
        orphan.setStudentId("s1");
        saved.save(orphan);
        reminders().sendReminders();
        assertTrue(notes.isEmpty());
        assertTrue(emails.isEmpty());
    }

    /** REGRESSION: a student who already applied was still told to "apply now". */
    @Test
    void noReminderOnceTheStudentHasAlreadyApplied() {
        Job j = job(JobStatus.OPEN, 1);
        SavedJob s = bookmark(j, "s1");
        JobApplication a = new JobApplication();
        a.setJobId(j.getId());
        a.setStudentId("s1");
        apps.save(a);
        DeadlineReminderScheduler r = reminders();
        r.sendReminders();
        assertTrue(notes.isEmpty());
        assertTrue(emails.isEmpty());
        assertTrue(saved.findById(s.getId()).get().isDeadlineReminderSent(), "marked done so it is not re-checked every day");
    }
}
