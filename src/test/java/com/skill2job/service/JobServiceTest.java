package com.skill2job.service;

import com.skill2job.dto.JobRequest;
import com.skill2job.dto.RequiredSkillRequest;
import com.skill2job.entity.Job;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.JobType;
import com.skill2job.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Job creation, editing, close/reopen, deletion and who may see what. */
class JobServiceTest {

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    // ------------------------------------------------------------------ creation / publication

    @Test
    void firstFreeJobsAreOpenAndPublishedImmediately() {
        JobFixture f = new JobFixture();
        for (int i = 0; i < JobFixture.FREE_POSTS; i++) {
            Job j = f.post("emp1");
            assertEquals(JobStatus.OPEN, j.getStatus());
            assertNotNull(j.getPublishedAt());
        }
    }

    @Test
    void jobAfterTheFreeQuotaWaitsForPaymentAndIsNotPublished() {
        JobFixture f = new JobFixture();
        for (int i = 0; i < JobFixture.FREE_POSTS; i++) f.post("emp1");
        Job j = f.post("emp1");
        assertEquals(JobStatus.PENDING_PAYMENT, j.getStatus());
        assertNull(j.getPublishedAt());
    }

    @Test
    void activeProEmployerIsNeverCharged() {
        JobFixture f = new JobFixture();
        for (int i = 0; i < JobFixture.FREE_POSTS; i++) f.post("pro");
        EmployerProfile profile = f.employerProfiles.get("pro");
        profile.setSubscriptionPlan(com.skill2job.enums.SubscriptionPlan.PRO);
        profile.setSubscriptionExpiresAt(Instant.now().plusSeconds(3600));
        assertEquals(JobStatus.OPEN, f.post("pro").getStatus());
    }

    @Test
    void deletingAJobNeverRefundsTheFreeSlot() {
        JobFixture f = new JobFixture();
        Job first = f.post("emp1");
        f.post("emp1");
        f.post("emp1");
        f.jobService.delete(JobFixture.employer("emp1"), first.getId());
        assertEquals(JobStatus.PENDING_PAYMENT, f.post("emp1").getStatus(), "the counter is persistent, not a live count");
    }

    @Test
    void createTrimsTextAndCopiesSkillNamesFromTheCatalogue() {
        JobFixture f = new JobFixture();
        Job j = f.post("emp1");
        assertEquals("Backend Intern", j.getTitle());
        assertEquals("Build APIs", j.getDescription());
        assertEquals("Dhaka", j.getLocation());
        assertEquals("JAVA", j.getRequiredSkills().get(0).getSkillName());
        assertEquals("Co emp1", j.getCompanyName());
    }

    @Test
    void deadlineBoundaries() {
        JobFixture f = new JobFixture();
        var emp = JobFixture.employer("emp1");
        assertEquals(400, status(() -> f.jobService.create(emp, JobFixture.request(JobFixture.in(-1)))), "yesterday");
        assertEquals(400, status(() -> f.jobService.create(emp, JobFixture.request(null))), "missing");
        assertEquals(400, status(() -> f.jobService.create(emp, JobFixture.request(JobService.today().plusYears(1).plusDays(1)))), "over a year");
        assertEquals(200, status(() -> f.jobService.create(emp, JobFixture.request(JobService.today()))), "today is allowed");
        assertEquals(200, status(() -> f.jobService.create(emp, JobFixture.request(JobService.today().plusYears(1)))), "exactly one year is allowed");
    }

    @Test
    void rejectedJobIsNotSavedAndDoesNotConsumeAFreeSlot() {
        JobFixture f = new JobFixture();
        var emp = JobFixture.employer("emp1");
        assertEquals(400, status(() -> f.jobService.create(emp, JobFixture.request(JobFixture.in(-5)))));
        assertEquals(400, status(() -> f.jobService.create(emp, JobFixture.request(JobFixture.in(5),
                new RequiredSkillRequest("java", 3, 3, false), new RequiredSkillRequest("java", 2, 1, true)))), "duplicate skill");
        assertEquals(400, status(() -> f.jobService.create(emp, JobFixture.request(JobFixture.in(5),
                new RequiredSkillRequest("cobol", 3, 3, false)))), "unknown skill");
        assertEquals(0, f.jobStore.size());
        assertEquals(0, f.employerProfiles.get("emp1").getJobsPostedCount());
    }

    // ------------------------------------------------------------------ editing / ownership

    @Test
    void onlyTheOwnerOrAnAdminCanEditCloseOrDelete() {
        JobFixture f = new JobFixture();
        Job j = f.post("emp1");
        var other = JobFixture.employer("emp2");
        JobRequest req = JobFixture.request(JobFixture.in(20));
        assertEquals(403, status(() -> f.jobService.update(other, j.getId(), req)));
        assertEquals(403, status(() -> f.jobService.close(other, j.getId())));
        assertEquals(403, status(() -> f.jobService.delete(other, j.getId())));
        assertEquals(403, status(() -> f.jobService.update(JobFixture.student("s1"), j.getId(), req)));
        assertEquals(200, status(() -> f.jobService.update(JobFixture.admin(), j.getId(), req)));
        assertEquals(200, status(() -> f.jobService.update(JobFixture.employer("emp1"), j.getId(), req)));
        assertEquals(JobFixture.in(20), f.jobs.findById(j.getId()).get().getDeadline());
    }

    @Test
    void editingKeepsStatusAndCompanyAndRevalidatesTheDeadline() {
        JobFixture f = new JobFixture();
        Job j = f.post("emp1");
        assertEquals(400, status(() -> f.jobService.update(JobFixture.employer("emp1"), j.getId(), JobFixture.request(JobFixture.in(-1)))));
        assertEquals(JobFixture.in(10), f.jobs.findById(j.getId()).get().getDeadline(), "failed edit changes nothing");
        f.jobService.update(JobFixture.employer("emp1"), j.getId(), JobFixture.request(JobFixture.in(11)));
        assertEquals(JobStatus.OPEN, f.jobs.findById(j.getId()).get().getStatus());
    }

    @Test
    void unknownJobIs404() {
        JobFixture f = new JobFixture();
        assertEquals(404, status(() -> f.jobService.get("nope")));
        assertEquals(404, status(() -> f.jobService.close(JobFixture.employer("emp1"), "nope")));
    }

    // ------------------------------------------------------------------ close / reopen

    @Test
    void closeAndReopenAnOpenJob() {
        JobFixture f = new JobFixture();
        Job j = f.post("emp1");
        f.jobService.close(JobFixture.employer("emp1"), j.getId());
        assertEquals(JobStatus.CLOSED, f.jobs.findById(j.getId()).get().getStatus());
        f.jobService.reopen(JobFixture.employer("emp1"), j.getId());
        assertEquals(JobStatus.OPEN, f.jobs.findById(j.getId()).get().getStatus());
    }

    @Test
    void reopenIsRefusedWhenTheDeadlineHasPassed() {
        JobFixture f = new JobFixture();
        Job j = f.post("emp1");
        f.jobService.close(JobFixture.employer("emp1"), j.getId());
        f.jobs.findById(j.getId()).get().setDeadline(JobFixture.in(-1));
        assertEquals(400, status(() -> f.jobService.reopen(JobFixture.employer("emp1"), j.getId())));
    }

    /** REGRESSION (audit F-02): close + reopen used to publish an unpaid job without paying the fee. */
    @Test
    void unpaidJobCannotBeClosedAndReopenedToSkipThePostingFee() {
        JobFixture f = new JobFixture();
        for (int i = 0; i < JobFixture.FREE_POSTS; i++) f.post("emp1");
        Job unpaid = f.post("emp1");
        var emp = JobFixture.employer("emp1");
        assertEquals(400, status(() -> f.jobService.close(emp, unpaid.getId())));
        assertEquals(400, status(() -> f.jobService.reopen(emp, unpaid.getId())));
        assertEquals(JobStatus.PENDING_PAYMENT, f.jobs.findById(unpaid.getId()).get().getStatus());
        assertNull(f.jobs.findById(unpaid.getId()).get().getPublishedAt());
    }

    // ------------------------------------------------------------------ visibility

    /** REGRESSION (audit F-13): GET /api/jobs/{id} and /compare used to show unpaid, unpublished jobs to anyone. */
    @Test
    void unpaidJobsAreInvisibleToThePublicStudentsAndOtherEmployers() {
        JobFixture f = new JobFixture();
        for (int i = 0; i < JobFixture.FREE_POSTS; i++) f.post("emp1");
        Job unpaid = f.post("emp1");
        assertEquals(404, status(() -> f.jobService.get(unpaid.getId(), null)), "anonymous visitor");
        assertEquals(404, status(() -> f.jobService.get(unpaid.getId(), JobFixture.student("s1"))));
        assertEquals(404, status(() -> f.jobService.get(unpaid.getId(), JobFixture.employer("emp2"))));
        assertEquals(200, status(() -> f.jobService.get(unpaid.getId(), JobFixture.employer("emp1"))), "owner can still pay for it");
        assertEquals(200, status(() -> f.jobService.get(unpaid.getId(), JobFixture.admin())));
        assertEquals(404, status(() -> f.jobService.getVisibleEntity(unpaid.getId(), null)));
        assertTrue(f.jobService.getMany(List.of(unpaid.getId()), null).isEmpty());
        assertEquals(1, f.jobService.getMany(List.of(unpaid.getId()), JobFixture.employer("emp1")).size());
    }

    @Test
    void openAndClosedJobsStayPublicAndCompareIsDistinctCappedAndSkipsUnknownIds() {
        JobFixture f = new JobFixture();
        Job a = f.post("emp1"), b = f.post("emp1"), c = f.post("emp1");
        f.jobService.close(JobFixture.employer("emp1"), c.getId());
        assertEquals(200, status(() -> f.jobService.get(c.getId(), null)));
        assertEquals(2, f.jobService.getMany(List.of(a.getId(), a.getId(), "ghost", b.getId()), null).size());
        List<String> many = List.of(a.getId(), b.getId(), c.getId(), "x1", "x2", "x3");
        assertTrue(f.jobService.getMany(many, null).size() <= 4);
        assertTrue(f.jobService.getMany(List.of(), null).isEmpty());
        assertTrue(f.jobService.getMany(null, null).isEmpty());
    }

    // ------------------------------------------------------------------ deletion

    @Test
    void deleteRemovesTheJobAndEverythingHangingOffItButNothingElse() {
        JobFixture f = new JobFixture();
        Job j = f.post("emp1");
        Job keep = f.post("emp1");
        for (Job x : new Job[]{j, keep}) {
            var app = new com.skill2job.entity.JobApplication();
            app.setJobId(x.getId());
            app.setStudentId("s-" + x.getId());
            f.apps.save(app);
            var sv = new com.skill2job.entity.SavedJob();
            sv.setJobId(x.getId());
            sv.setStudentId("s-" + x.getId());
            f.saved.save(sv);
            var iv = new com.skill2job.entity.Interview();
            iv.setJobId(x.getId());
            f.interviews.save(iv);
        }
        f.jobService.delete(JobFixture.employer("emp1"), j.getId());
        assertTrue(f.jobs.findById(j.getId()).isEmpty());
        assertTrue(f.jobs.findById(keep.getId()).isPresent());
        assertEquals(1, f.apps.countByJobId(keep.getId()));
        assertEquals(0, f.apps.countByJobId(j.getId()));
        assertEquals(1, f.savedStore.size());
        assertEquals(1, f.interviewStore.size());
    }
}
