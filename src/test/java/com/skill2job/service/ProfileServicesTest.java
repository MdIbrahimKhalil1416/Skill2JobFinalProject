package com.skill2job.service;

import com.skill2job.dto.CourseProgressRequest;
import com.skill2job.dto.EmployerProfileRequest;
import com.skill2job.dto.StudentProfileRequest;
import com.skill2job.dto.StudentSkillRequest;
import com.skill2job.dto.StudentProfileResponse;
import com.skill2job.entity.Course;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Job;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.CourseProgressStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.CourseRepository;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Student profile / skills / course progress / endorsements and employer profile + logo behaviour. */
class ProfileServicesTest {

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    // ================================================================== student

    final Map<String, Object> studentStore = new LinkedHashMap<>();
    final Map<String, Object> courseStore = new LinkedHashMap<>();
    final StudentProfileRepository studentRepo = MapRepo.of(StudentProfileRepository.class, studentStore);
    final CourseRepository courseRepo = MapRepo.of(CourseRepository.class, courseStore);
    final List<String> xp = new ArrayList<>();
    final List<String> notes = new ArrayList<>();
    final StudentService students;

    ProfileServicesTest() {
        SkillService skills = new SkillService(null, null, null, null) {
            @Override public Skill getEntity(String id) {
                if (id.startsWith("missing")) throw ApiException.notFound("Skill not found.");
                Skill s = new Skill();
                s.setId(id);
                s.setName(id.substring(0, 1).toUpperCase() + id.substring(1));
                return s;
            }
        };
        GamificationService game = new GamificationService(null, null, null, null, null, null) {
            @Override public void award(String id, int amount, String reason, String key) { xp.add(key); }
        };
        students = new StudentService(studentRepo, MapRepo.of(UserRepository.class), skills, courseRepo, game,
                new NotificationService(null) {
                    @Override public void notify(String userId, String message, String link) { notes.add(userId + "|" + message + "|" + link); }
                });
    }

    @Test
    void profileUpdateTrimsAndBlanksBecomeNull() {
        students.updateProfile("s1", new StudentProfileRequest("  Sara  ", " CSE ", 3.5, 2026, "   ", " https://gh.io/x ", "", null));
        StudentProfile p = studentRepo.findByUserId("s1").get();
        assertEquals("Sara", p.getFullName());
        assertEquals("CSE", p.getDepartment());
        assertNull(p.getBio());
        assertEquals("https://gh.io/x", p.getGithubUrl());
        assertNull(p.getLinkedinUrl());
        assertEquals(3.5, p.getCgpa());
    }

    @Test
    void addingSkillTwiceUpdatesTheLevelWithoutDuplicatesOrExtraXp() {
        students.upsertSkill("s1", new StudentSkillRequest("java", 2));
        students.upsertSkill("s1", new StudentSkillRequest("java", 4));
        StudentProfile p = studentRepo.findByUserId("s1").get();
        assertEquals(1, p.getSkills().size());
        assertEquals(4, p.getSkills().get(0).getLevel());
        assertEquals(List.of("SKILL:java"), xp, "XP only for the first add");
    }

    @Test
    void skillsAreKeptSortedByNameCaseInsensitively() {
        for (String s : new String[]{"sql", "java", "react"}) students.upsertSkill("s1", new StudentSkillRequest(s, 3));
        List<String> names = studentRepo.findByUserId("s1").get().getSkills().stream().map(x -> x.getSkillName()).toList();
        assertEquals(List.of("Java", "React", "Sql"), names);
    }

    @Test
    void skillLimitIsEnforcedButExistingSkillsCanStillBeEdited() {
        for (int i = 0; i < StudentService.MAX_SKILLS; i++) students.upsertSkill("s1", new StudentSkillRequest("skill" + i, 1));
        assertEquals(400, status(() -> students.upsertSkill("s1", new StudentSkillRequest("one-too-many", 1))));
        assertEquals(200, status(() -> students.upsertSkill("s1", new StudentSkillRequest("skill0", 5))));
        assertEquals(StudentService.MAX_SKILLS, studentRepo.findByUserId("s1").get().getSkills().size());
    }

    @Test
    void unknownSkillAndRemovingASkillYouDoNotHaveAre404() {
        assertEquals(404, status(() -> students.upsertSkill("s1", new StudentSkillRequest("missing-x", 3))));
        assertEquals(404, status(() -> students.removeSkill("s1", "java")));
        students.upsertSkill("s1", new StudentSkillRequest("java", 3));
        students.removeSkill("s1", "java");
        assertTrue(studentRepo.findByUserId("s1").get().getSkills().isEmpty());
    }

    private Course course(String id, String title) {
        Course c = new Course();
        c.setId(id);
        c.setTitle(title);
        courseStore.put(id, c);
        return c;
    }

    @Test
    void courseProgressStartCompleteAndRemove() {
        course("c1", "Java 101");
        students.upsertCourseProgress("s1", new CourseProgressRequest("c1", CourseProgressStatus.IN_PROGRESS));
        students.upsertCourseProgress("s1", new CourseProgressRequest("c1", CourseProgressStatus.COMPLETED));
        StudentProfile p = studentRepo.findByUserId("s1").get();
        assertEquals(1, p.getCourseProgress().size(), "one row per course");
        assertEquals(CourseProgressStatus.COMPLETED, p.getCourseProgress().get(0).getStatus());
        assertEquals(List.of("COURSE_START:c1", "COURSE_DONE:c1"), xp);
        assertEquals(404, status(() -> students.upsertCourseProgress("s1", new CourseProgressRequest("ghost", CourseProgressStatus.COMPLETED))));
        students.removeCourseProgress("s1", "c1");
        assertEquals(404, status(() -> students.removeCourseProgress("s1", "c1")));
    }

    @Test
    void endorsementRules() {
        students.upsertSkill("s1", new StudentSkillRequest("java", 3));
        var peer = JobFixture.student("s2");
        assertEquals(400, status(() -> students.setEndorsement(JobFixture.student("s1"), "s1", "java", true)), "no self-endorsement");
        assertEquals(403, status(() -> students.setEndorsement(JobFixture.employer("emp1"), "s1", "java", true)), "employers cannot endorse");
        assertEquals(404, status(() -> students.setEndorsement(peer, "s1", "sql", true)), "skill must exist on the profile");
        students.setEndorsement(peer, "s1", "java", true);
        var skill = studentRepo.findByUserId("s1").get().getSkills().get(0);
        assertTrue(skill.isEndorsed());
        assertEquals("s2", skill.getEndorsedByName());
        students.setEndorsement(JobFixture.admin(), "s1", "java", false);
        skill = studentRepo.findByUserId("s1").get().getSkills().get(0);
        assertFalse(skill.isEndorsed());
        assertNull(skill.getEndorsedByName());
        assertNull(skill.getEndorsedAt());
    }

    @Test
    void aNewEndorsementNotifiesTheStudentOnceWithALinkToTheirSkills() {
        students.upsertSkill("s1", new StudentSkillRequest("java", 3));
        students.setEndorsement(JobFixture.student("s2"), "s1", "java", true);
        students.setEndorsement(JobFixture.student("s2"), "s1", "java", true);      // already endorsed: no second note
        students.setEndorsement(JobFixture.admin(), "s1", "java", false);           // removal: no note
        assertEquals(1, notes.size(), notes.toString());
        assertTrue(notes.get(0).startsWith("s1|s2 endorsed your skill"), notes.get(0));
        assertTrue(notes.get(0).endsWith("|/profile.html"));
    }

    @Test
    void cvClearOnlyWhenTheReferenceStillMatches() {
        StudentProfile p = students.getOrCreate("s1");
        assertEquals(404, status(() -> students.clearCv("s1")), "no CV to delete");
        students.setCv("s1", "a.pdf", "cv.pdf");
        students.clearCvIfStored("s1", "other.pdf");
        assertTrue(p.hasCv());
        students.clearCvIfStored("s1", "a.pdf");
        assertFalse(p.hasCv());
    }

    @Test
    void findProfileNeverCreatesAnything() {
        assertTrue(students.findProfile("ghost").isEmpty());
        assertEquals(0, studentStore.size());
    }

    // ================================================================== employer

    final Map<String, Object> employerStore = new LinkedHashMap<>();
    final EmployerProfileRepository employerRepo = MapRepo.of(EmployerProfileRepository.class, employerStore);
    final JobFixture jf = new JobFixture();

    private EmployerService employers(ImageStorageService images) {
        return new EmployerService(employerRepo, MapRepo.of(UserRepository.class), jf.jobs, images);
    }

    private static Path logoDir() throws IOException {
        return Files.createTempDirectory("s2j-employer-logo");
    }

    private static long count(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) { return s.count(); }
    }

    @Test
    void renamingTheCompanyUpdatesItsJobsAndNoOneElsesJobs() throws IOException {
        EmployerService svc = employers(new ImageStorageService(logoDir().toString(), 2));
        svc.getOrCreate("emp1");
        Job mine = jf.post("emp1");
        Job theirs = jf.post("emp2");
        mine.setEmployerId("emp1");
        svc.updateProfile("emp1", new EmployerProfileRequest(" New Name Ltd ", "", "  ", null, " about "));
        assertEquals("New Name Ltd", employerRepo.findByUserId("emp1").get().getCompanyName());
        assertNull(employerRepo.findByUserId("emp1").get().getWebsite());
        assertEquals("New Name Ltd", jf.jobs.findById(mine.getId()).get().getCompanyName());
        assertEquals("Co emp2", jf.jobs.findById(theirs.getId()).get().getCompanyName());
    }

    @Test
    void publicProfileOfAnUnknownCompanyIs404AndCreatesNothing() throws IOException {
        EmployerService svc = employers(new ImageStorageService(logoDir().toString(), 2));
        assertEquals(404, status(() -> svc.getPublicProfile("ghost")));
        assertEquals(0, employerStore.size(), "a public GET must not write to the database");
    }

    @Test
    void logoUploadReplacementKeepsExactlyOneFileAndTheOldOneIsGoneOnlyAfterTheSave() throws IOException {
        Path dir = logoDir();
        EmployerService svc = employers(new ImageStorageService(dir.toString(), 2));
        svc.getOrCreate("emp1");
        svc.uploadLogo("emp1", new FakeUpload("a.png", "image/png", FakeUpload.image("png", 20, 20)));
        String first = employerRepo.findByUserId("emp1").get().getLogoStoredFilename();
        svc.uploadLogo("emp1", new FakeUpload("b.png", "image/png", FakeUpload.image("png", 30, 30)));
        String second = employerRepo.findByUserId("emp1").get().getLogoStoredFilename();
        assertFalse(first.equals(second));
        assertEquals(1, count(dir));
        assertEquals("b.png", employerRepo.findByUserId("emp1").get().getLogoOriginalFilename());
    }

    @Test
    void invalidLogoLeavesTheCurrentLogoUntouched() throws IOException {
        Path dir = logoDir();
        EmployerService svc = employers(new ImageStorageService(dir.toString(), 2));
        svc.getOrCreate("emp1");
        svc.uploadLogo("emp1", new FakeUpload("a.png", "image/png", FakeUpload.image("png", 20, 20)));
        String first = employerRepo.findByUserId("emp1").get().getLogoStoredFilename();
        assertEquals(400, status(() -> svc.uploadLogo("emp1", new FakeUpload("evil.png", "image/png", FakeUpload.text("<html>")))));
        assertEquals(first, employerRepo.findByUserId("emp1").get().getLogoStoredFilename());
        assertEquals(1, count(dir));
    }

    @Test
    void databaseFailureWhileSavingTheLogoDropsTheNewFileAndKeepsTheOldOne() throws IOException {
        Path dir = logoDir();
        boolean[] failSave = {false};
        EmployerProfileRepository flaky = (EmployerProfileRepository) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{EmployerProfileRepository.class}, (proxy, m, args) -> {
                    if (m.getName().equals("save") && failSave[0]) throw new IllegalStateException("db down");
                    try { return m.invoke(employerRepo, args); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                });
        EmployerService svc = new EmployerService(flaky, MapRepo.of(UserRepository.class), jf.jobs, new ImageStorageService(dir.toString(), 2));
        svc.getOrCreate("emp1");
        svc.uploadLogo("emp1", new FakeUpload("a.png", "image/png", FakeUpload.image("png", 20, 20)));
        String first = employerRepo.findByUserId("emp1").get().getLogoStoredFilename();
        failSave[0] = true;
        assertThrows(IllegalStateException.class,
                () -> svc.uploadLogo("emp1", new FakeUpload("b.png", "image/png", FakeUpload.image("png", 30, 30))));
        assertEquals(1, count(dir), "only the old logo remains on disk");
        assertTrue(Files.exists(dir.resolve(first)));
    }

    @Test
    void missingLogoFileClearsTheStaleReferenceAnd404s() throws IOException {
        Path dir = logoDir();
        EmployerService svc = employers(new ImageStorageService(dir.toString(), 2));
        svc.getOrCreate("emp1");
        svc.uploadLogo("emp1", new FakeUpload("a.png", "image/png", FakeUpload.image("png", 20, 20)));
        EmployerService.LogoFile ok = svc.getLogo("emp1");
        assertEquals("image/png", ok.contentType());
        Files.delete(ok.path());
        assertEquals(404, status(() -> svc.getLogo("emp1")));
        assertFalse(employerRepo.findByUserId("emp1").get().hasLogo());
        assertEquals(404, status(() -> svc.getLogo("ghost")));
    }
}
