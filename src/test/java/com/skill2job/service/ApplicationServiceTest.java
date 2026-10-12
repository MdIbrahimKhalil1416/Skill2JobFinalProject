package com.skill2job.service;

import com.skill2job.dto.ApplicationResponse;
import com.skill2job.dto.RequiredSkillRequest;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.JobStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.NotificationRepository;
import com.skill2job.security.AuthUser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Applying (deadline, duplicates, mandatory skills) and the shortlist/hire/reject workflow. */
class ApplicationServiceTest {

    final JobFixture f = new JobFixture();
    final Map<String, StudentProfile> profiles = new HashMap<>();
    final List<String> notifications = new ArrayList<>();
    final List<String> xp = new ArrayList<>();
    final RecordingEmailService mail = new RecordingEmailService();
    final ApplicationService svc;

    ApplicationServiceTest() {
        StudentService students = new StudentService(null, null, null, null, null, null) {
            @Override public StudentProfile getOrCreate(String userId) {
                return profiles.computeIfAbsent(userId, id -> JobFixture.studentProfile(id));
            }
        };
        NotificationService notes = new NotificationService(null) {
            @Override public void notify(String userId, String message, String link) { notifications.add(userId + ": " + message); }
        };
        GamificationService game = new GamificationService(null, null, null, null, null, null) {
            @Override public void award(String id, int amount, String reason, String key) { xp.add(id + ":" + amount + ":" + key); }
        };
        MatchingService matching = new MatchingService(null, null, null, null);
        svc = new ApplicationService(f.apps, f.jobService, students, matching, notes, mail, null, game);
    }

    private Job job(String employer, RequiredSkillRequest... skills) {
        String id = f.jobService.create(JobFixture.employer(employer), JobFixture.request(JobFixture.in(7), skills)).id();
        return f.jobs.findById(id).get();
    }

    private void skills(String student, String... skillLevels) {
        profiles.put(student, JobFixture.studentProfile(student, skillLevels));
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    // ------------------------------------------------------------------ applying

    @Test
    void qualifiedStudentCanApplyAndEmployerIsNotifiedAndXpIsAwardedOnce() {
        Job j = job("emp1");
        skills("s1", "java:4");
        ApplicationResponse r = svc.apply(JobFixture.student("s1"), j.getId(), false);
        assertEquals(ApplicationStatus.APPLIED, r.status());
        assertTrue(r.matchPercent() > 0);
        assertEquals(1, f.appStore.size());
        assertTrue(notifications.get(0).startsWith("emp1:"));
        assertEquals(List.of("s1:10:APPLY:" + j.getId()), xp);
    }

    @Test
    void duplicateApplicationIsRejectedWithoutSideEffects() {
        Job j = job("emp1");
        skills("s1", "java:4");
        svc.apply(JobFixture.student("s1"), j.getId(), false);
        assertEquals(409, status(() -> svc.apply(JobFixture.student("s1"), j.getId(), false)));
        assertEquals(409, status(() -> svc.apply(JobFixture.student("s1"), j.getId(), true)), "force does not bypass duplicates");
        assertEquals(1, f.appStore.size());
        assertEquals(1, notifications.size());
        assertEquals(1, xp.size());
    }

    @Test
    void deadlineBoundary_todayIsStillOpen_yesterdayIsNot() {
        Job j = job("emp1");
        skills("s1", "java:4");
        j.setDeadline(JobService.today());
        assertEquals(200, status(() -> svc.apply(JobFixture.student("s1"), j.getId(), false)), "deadline day itself is allowed");
        skills("s2", "java:4");
        j.setDeadline(JobFixture.in(-1));
        assertEquals(400, status(() -> svc.apply(JobFixture.student("s2"), j.getId(), false)), "day after the deadline");
    }

    @Test
    void closedUnpaidAndMissingJobsDoNotAcceptApplications() {
        Job j = job("emp1");
        skills("s1", "java:4");
        f.jobService.close(JobFixture.employer("emp1"), j.getId());
        assertEquals(400, status(() -> svc.apply(JobFixture.student("s1"), j.getId(), false)));
        j.setStatus(JobStatus.PENDING_PAYMENT);
        assertEquals(400, status(() -> svc.apply(JobFixture.student("s1"), j.getId(), false)));
        assertEquals(404, status(() -> svc.apply(JobFixture.student("s1"), "ghost", false)));
        assertEquals(0, f.appStore.size());
    }

    @Test
    void studentWithoutSkillsMustAddSkillsFirst() {
        Job j = job("emp1");
        assertEquals(400, status(() -> svc.apply(JobFixture.student("s1"), j.getId(), false)));
        assertEquals(0, f.appStore.size());
    }

    @Test
    void missingMandatorySkillNeedsExplicitConfirmation() {
        Job j = job("emp1", new RequiredSkillRequest("java", 3, 3, true));
        skills("s1", "sql:5");
        assertEquals(400, status(() -> svc.apply(JobFixture.student("s1"), j.getId(), false)));
        assertEquals(0, f.appStore.size());
        ApplicationResponse r = svc.apply(JobFixture.student("s1"), j.getId(), true);
        assertFalse(r.eligible());
    }

    // ------------------------------------------------------------------ status workflow

    private String applied(Job j, String student) {
        skills(student, "java:4");
        return svc.apply(JobFixture.student(student), j.getId(), false).id();
    }

    @Test
    void fullHiringPathNotifiesTheStudentAndEmailsEachStep() {
        Job j = job("emp1");
        String id = applied(j, "s1");
        var emp = JobFixture.employer("emp1");
        assertEquals(ApplicationStatus.SHORTLISTED, svc.updateStatus(emp, id, ApplicationStatus.SHORTLISTED).status());
        assertEquals(ApplicationStatus.HIRED, svc.updateStatus(emp, id, ApplicationStatus.HIRED).status());
        assertEquals(2, mail.sent.stream().filter(s -> s.kind().startsWith("status:")).count());
        assertTrue(notifications.stream().anyMatch(n -> n.startsWith("s1:") && n.contains("HIRED")));
    }

    @Test
    void illegalTransitionsAreRefusedAndChangeNothing() {
        Job j = job("emp1");
        String id = applied(j, "s1");
        var emp = JobFixture.employer("emp1");
        assertEquals(400, status(() -> svc.updateStatus(emp, id, ApplicationStatus.HIRED)), "APPLIED cannot jump to HIRED");
        assertEquals(400, status(() -> svc.updateStatus(emp, id, ApplicationStatus.APPLIED)));
        svc.updateStatus(emp, id, ApplicationStatus.REJECTED);
        assertEquals(400, status(() -> svc.updateStatus(emp, id, ApplicationStatus.SHORTLISTED)), "REJECTED is final");
        assertEquals(ApplicationStatus.REJECTED, f.apps.findById(id).get().getStatus());
    }

    @Test
    void onlyTheJobOwnerOrAdminCanChangeStatus() {
        Job j = job("emp1");
        String id = applied(j, "s1");
        assertEquals(403, status(() -> svc.updateStatus(JobFixture.employer("emp2"), id, ApplicationStatus.SHORTLISTED)));
        assertEquals(403, status(() -> svc.updateStatus(JobFixture.student("s1"), id, ApplicationStatus.HIRED)), "students cannot hire themselves");
        assertEquals(200, status(() -> svc.updateStatus(JobFixture.admin(), id, ApplicationStatus.SHORTLISTED)));
        assertEquals(404, status(() -> svc.updateStatus(JobFixture.employer("emp1"), "ghost", ApplicationStatus.SHORTLISTED)));
    }

    @Test
    void failingEmailNeverBlocksAStatusChange() {
        Job j = job("emp1");
        String id = applied(j, "s1");
        mail.failing = true;
        assertEquals(ApplicationStatus.SHORTLISTED, svc.updateStatus(JobFixture.employer("emp1"), id, ApplicationStatus.SHORTLISTED).status());
    }

    @Test
    void bulkUpdateSkipsInvalidItemsWithAReasonAndAppliesTheRest() {
        Job j = job("emp1");
        String a = applied(j, "s1");
        String b = applied(j, "s2");
        var emp = JobFixture.employer("emp1");
        svc.updateStatus(emp, b, ApplicationStatus.REJECTED);          // b is final now
        Map<String, Object> result = svc.bulkUpdateStatus(emp, List.of(a, b, "ghost"), ApplicationStatus.SHORTLISTED);
        assertEquals(List.of(a), result.get("updated"));
        @SuppressWarnings("unchecked") Map<String, String> skipped = (Map<String, String>) result.get("skipped");
        assertEquals(2, skipped.size());
        assertTrue(skipped.containsKey(b) && skipped.containsKey("ghost"));
    }

    @Test
    void mineListsOnlyTheStudentsOwnApplications() {
        Job j = job("emp1");
        applied(j, "s1");
        applied(j, "s2");
        assertEquals(1, svc.mine("s1").size());
        assertEquals("s1", svc.mine("s1").get(0).studentId());
    }
}
