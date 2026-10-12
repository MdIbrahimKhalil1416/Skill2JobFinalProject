package com.skill2job.service;

import com.skill2job.entity.Course;
import com.skill2job.entity.Job;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.JobStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.CourseRepository;
import com.skill2job.repository.QuizQuestionRepository;
import com.skill2job.roadmap.RoadmapService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Learning roadmap: a plan for one job, the market plan, and not leaking unpublished jobs. */
class RoadmapServiceTest {

    final JobFixture fx = new JobFixture();
    final Map<String, Object> courseStore = new LinkedHashMap<>();
    final CourseRepository courses = MapRepo.of(CourseRepository.class, courseStore);
    final QuizQuestionRepository quiz = MapRepo.of(QuizQuestionRepository.class);
    final Map<String, StudentProfile> profiles = new LinkedHashMap<>();
    final RoadmapService svc;

    RoadmapServiceTest() {
        StudentService students = new StudentService(null, null, null, null, null, null) {
            @Override public StudentProfile getOrCreate(String id) {
                return profiles.computeIfAbsent(id, x -> JobFixture.studentProfile(x));
            }
        };
        svc = new RoadmapService(students, fx.jobService, fx.jobs, courses, quiz);
    }

    private void course(String id, String skill, int level) {
        Course c = new Course();
        c.setId(id);
        c.setTitle("Course " + id);
        c.setPlatform("Udemy");
        c.setUrl("https://example.com/" + id);
        c.setLevel(level);
        c.setFree(true);
        c.setSkillIds(List.of(skill));
        courseStore.put(id, c);
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void jobRoadmapListsTheMissingSkillAndRaisesTheProjectedMatch() {
        Job job = fx.post("e1");               // requires java level 3
        course("c1", "java", 3);
        var r = svc.build("s1", job.getId(), 10);
        assertEquals("JOB", r.target());
        assertEquals(job.getId(), r.jobId());
        assertEquals(1, r.skills().size());
        assertEquals("java", r.skills().get(0).skillId());
        assertEquals(0, r.skills().get(0).fromLevel());
        assertEquals(3, r.skills().get(0).toLevel());
        assertTrue(r.projectedMatch() > r.currentMatch());
        assertFalse(r.weeks().isEmpty());
        assertEquals(10, r.hoursPerWeek());
    }

    @Test
    void aStudentWhoAlreadyMeetsTheJobNeedsNoWeeks() {
        Job job = fx.post("e1");
        profiles.put("s1", JobFixture.studentProfile("s1", "java:4"));
        var r = svc.build("s1", job.getId(), 10);
        assertTrue(r.skills().isEmpty());
        assertTrue(r.weeks().isEmpty());
        assertEquals(0, r.totalWeeks());
    }

    @Test
    void hoursPerWeekIsClampedToTwoToForty() {
        Job job = fx.post("e1");
        assertEquals(2, svc.build("s1", job.getId(), 0).hoursPerWeek());
        assertEquals(40, svc.build("s1", job.getId(), 500).hoursPerWeek());
    }

    @Test
    void aSkillWithoutAnyCourseIsReportedNotSilentlyDropped() {
        Job job = fx.post("e1");
        var r = svc.build("s1", job.getId(), 10);
        assertEquals(List.of("JAVA"), r.noCourseSkills().stream().map(String::toUpperCase).toList());
    }

    @Test
    void unknownJobIs404() {
        assertEquals(404, status(() -> svc.build("s1", "missing", 10)));
    }

    @Test
    void aJobWaitingForItsPostingFeeDoesNotExistForStudents() {
        Job job = fx.post("e1");
        job.setStatus(JobStatus.PENDING_PAYMENT);
        assertEquals(404, status(() -> svc.build("s1", job.getId(), 10)),
                "an unpaid, unpublished job must not be readable through the roadmap");
    }

    @Test
    void marketRoadmapUsesTheSkillsOpenJobsAskForMostAndIgnoresClosedJobs() {
        fx.post("e1");
        fx.post("e2");
        Job closed = fx.post("e3");
        closed.setStatus(JobStatus.CLOSED);
        var r = svc.build("s1", null, 10);
        assertEquals("MARKET", r.target());
        assertNull(r.jobId());
        assertEquals(List.of("java"), r.skills().stream().map(RoadmapService.SkillTarget::skillId).toList());
        assertTrue(r.projectedMatch() >= r.currentMatch());
    }

    @Test
    void marketRoadmapWithNoOpenJobsIsEmptyAndZero() {
        var r = svc.build("s1", " ", 10);
        assertEquals("MARKET", r.target());
        assertTrue(r.skills().isEmpty());
        assertEquals(0.0, r.currentMatch());
    }
}
