package com.skill2job.alerts;

import com.skill2job.entity.Job;
import com.skill2job.entity.JobAlert;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.entity.User;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.JobType;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.JobAlertRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.service.EmailService;
import com.skill2job.service.JobService;
import com.skill2job.service.MapRepoAccess;
import com.skill2job.service.NotificationService;
import com.skill2job.service.SkillService;
import com.skill2job.service.StudentService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Saved searches: caps, filters, the (lastChecked, now] window, minimum match, email flag and failure isolation. */
class JobAlertServiceTest {

    final Map<String, Object> alertStore = new LinkedHashMap<>(), userStore = new LinkedHashMap<>();
    final JobAlertRepository alerts = MapRepoAccess.of(JobAlertRepository.class, alertStore);
    final List<Job> openJobs = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    final List<String> emails = new ArrayList<>();
    final StudentProfile student = new StudentProfile();
    final JobAlertService svc;

    JobAlertServiceTest() {
        student.setUserId("s1");
        student.getSkills().add(new StudentSkill("java", "Java", 4));
        User u = new User();
        u.setId("s1");
        u.setEmail("s1@uni.edu");
        u.setName("S1");
        userStore.put("s1", u);
        JobService jobs = new JobService(null, null, null, null, null, null, null, null, null) {
            @Override public List<Job> findOpenJobs(String k, JobType t, String l, String s) {
                return openJobs.stream().filter(j -> j.getStatus() == JobStatus.OPEN)
                        .filter(j -> k == null || j.getTitle().toLowerCase().contains(k.toLowerCase())).toList();
            }
        };
        StudentService students = new StudentService(null, null, null, null, null, null) {
            @Override public StudentProfile getOrCreate(String id) {
                if (id == null) throw new IllegalStateException("corrupt alert: no owner");
                return student;
            }
        };
        SkillService skills = new SkillService(null, null, null, null) {
            @Override public Skill getEntity(String id) { Skill s = new Skill(); s.setId(id); s.setName(id.toUpperCase()); return s; }
        };
        NotificationService notifier = new NotificationService(null) {
            @Override public void notify(String u, String m, String link) { notes.add(m); }
        };
        EmailService mail = new EmailService(null) {
            @Override public boolean sendJobAlertEmail(String to, String n, String a, List<String> l, String url) { emails.add(to + "|" + l.size()); return true; }
        };
        GamificationService game = new GamificationService(null, null, null, null, null, null) {
            @Override public void award(String id, int xp, String r, String k) { }
        };
        svc = new JobAlertService(alerts, jobs, students, skills, notifier, mail, MapRepoAccess.of(UserRepository.class, userStore), game);
    }

    private Job job(String title, Instant published, int javaLevelNeeded) {
        Job j = new Job();
        j.setId("job-" + openJobs.size());
        j.setTitle(title);
        j.setCompanyName("Co");
        j.setType(JobType.INTERNSHIP);
        j.setStatus(JobStatus.OPEN);
        j.setDeadline(LocalDate.now().plusDays(5));
        j.setPublishedAt(published);
        j.setRequiredSkills(List.of(new RequiredSkill("java", "Java", javaLevelNeeded, 3, false)));
        openJobs.add(j);
        return j;
    }

    /** Pretends every alert was last checked an hour ago, so "published 30 minutes ago" is inside the window. */
    private Instant ageAlerts() {
        Instant hourAgo = Instant.now().minusSeconds(3600);
        for (JobAlert a : alerts.findAll()) a.setLastCheckedAt(hourAgo);
        return hourAgo;
    }

    private JobAlertDtos.AlertRequest req(String name, String keyword, Integer minMatch, boolean email) {
        return new JobAlertDtos.AlertRequest(name, keyword, null, null, null, minMatch, email, null);
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void createRequiresAtLeastOneFilterAndRespectsTheCap() {
        assertEquals(400, status(() -> svc.create("s1", req("Empty", "  ", 0, false))), "no filter at all");
        for (int i = 0; i < JobAlertService.MAX_ALERTS; i++) svc.create("s1", req("A" + i, "java", null, false));
        assertEquals(400, status(() -> svc.create("s1", req("one more", "java", null, false))));
        assertEquals(JobAlertService.MAX_ALERTS, svc.mine("s1").size());
        assertEquals(10, JobAlertService.maxAlerts(false));
        assertEquals(30, JobAlertService.maxAlerts(true));
    }

    @Test
    void studentProRaisesTheCap() {
        student.setProExpiresAt(Instant.now().plusSeconds(3600));
        for (int i = 0; i < JobAlertService.MAX_ALERTS + 5; i++) svc.create("s1", req("A" + i, "java", null, false));
        assertEquals(JobAlertService.MAX_ALERTS + 5, svc.mine("s1").size());
    }

    @Test
    void alertsArePrivateToTheirOwner() {
        String id = svc.create("s1", req("Mine", "java", null, false)).id();
        assertEquals(404, status(() -> svc.update("s2", id, req("Hijack", "x", null, false))));
        assertEquals(404, status(() -> svc.delete("s2", id)));
        assertEquals(404, status(() -> svc.preview("s2", id)));
        svc.delete("s1", id);
        assertTrue(svc.mine("s1").isEmpty());
    }

    @Test
    void onlyJobsPublishedAfterTheLastCheckAreSent_andNeverTwice() {
        String id = svc.create("s1", req("Java jobs", "java", null, false)).id();
        Instant checked = ageAlerts();
        job("Old java job", checked.minusSeconds(60), 3);             // published before the last check
        job("New java job", checked.plusSeconds(1800), 3);
        job("Python job", checked.plusSeconds(1800), 3);              // does not match the keyword
        assertEquals(1, svc.runForStudent("s1").newJobs());
        assertEquals(1, notes.size());
        assertTrue(notes.get(0).contains("New java job"));
        assertEquals(0, svc.runForStudent("s1").newJobs(), "already sent, not again");
        assertEquals(1, notes.size());
        assertEquals(1, alerts.findById(id).get().getTotalJobsSent());
    }

    @Test
    void aJobPublishedAfterTheCheckStartedWaitsForTheNextRun() {
        String id = svc.create("s1", req("Java jobs", "java", null, false)).id();
        job("Future java job", Instant.now().plusSeconds(600), 3);
        ageAlerts();
        assertEquals(0, svc.runForStudent("s1").newJobs());
        assertEquals(0, alerts.findById(id).get().getTotalJobsSent());
        assertTrue(notes.isEmpty());
    }

    @Test
    void minimumMatchFiltersWeakMatches() {
        svc.create("s1", req("Strong only", "java", 90, false));
        Instant after = ageAlerts().plusSeconds(1800);
        job("Easy java", after, 2);        // student level 4 >= 2: full match
        job("Hard java", after, 5);        // student level 4 < 5: partial match
        svc.runForStudent("s1");
        assertEquals(1, notes.size());
        assertTrue(notes.get(0).contains("Easy java") && !notes.get(0).contains("Hard java"));
    }

    @Test
    void emailGoesOutOnlyWhenEnabledAndPausedAlertsAreSkipped() {
        svc.create("s1", req("Mail me", "java", null, true));
        svc.create("s1", req("No mail", "java", null, false));
        JobAlertDtos.AlertRequest paused = new JobAlertDtos.AlertRequest("Paused", "java", null, null, null, null, true, false);
        svc.create("s1", paused);
        job("A java job", ageAlerts().plusSeconds(1800), 3);
        JobAlertDtos.RunResult r = svc.runForStudent("s1");
        assertEquals(2, r.alertsChecked(), "the paused alert is not checked");
        assertEquals(1, emails.size());
        assertEquals("s1@uni.edu|1", emails.get(0));
        assertEquals(2, notes.size());
    }

    @Test
    void oneBrokenAlertDoesNotStopTheOthersInTheScheduledRun() {
        svc.create("s1", req("Good", "java", null, false));
        JobAlert broken = new JobAlert();
        broken.setStudentId(null);                     // will blow up when the profile is looked up / used
        broken.setActive(true);
        broken.setName("Broken");
        alerts.save(broken);
        job("java thing", ageAlerts().plusSeconds(1800), 3);
        JobAlertDtos.RunResult r = svc.runAll();       // must not throw
        assertEquals(2, r.alertsChecked());
        assertEquals(1, r.newJobs(), "the healthy alert was still processed");
        assertEquals(1, notes.size());
    }

    @Test
    void previewListsCurrentMatchesBestFirst() {
        String id = svc.create("s1", req("Java jobs", "java", null, false)).id();
        job("java hard", Instant.now().minusSeconds(100), 5);
        job("java easy", Instant.now().minusSeconds(100), 1);
        var p = svc.preview("s1", id);
        assertEquals(2, p.size());
        assertEquals("java easy", p.get(0).job().title());
    }
}
