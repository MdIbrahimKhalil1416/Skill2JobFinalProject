package com.skill2job.service;

import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.security.AuthUser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Who may download a CV, what happens when the file is gone, and that a failed save never loses the old CV. */
class CvServiceTest {

    /** In-memory stand-in for StudentService: profiles by user id, optional save failure. */
    static class FakeStudents extends StudentService {
        final Map<String, StudentProfile> profiles = new HashMap<>();
        boolean failSave;
        int created;

        FakeStudents() { super(null, null, null, null, null, null); }

        @Override public Optional<StudentProfile> findProfile(String userId) { return Optional.ofNullable(profiles.get(userId)); }

        @Override public StudentProfile getOrCreate(String userId) {
            created++;
            return profiles.computeIfAbsent(userId, id -> { StudentProfile p = new StudentProfile(); p.setUserId(id); return p; });
        }

        @Override public void clearCvIfStored(String userId, String stored) {
            StudentProfile p = profiles.get(userId);
            if (p != null && stored.equals(p.getCvStoredFilename())) { p.setCvStoredFilename(null); p.setCvOriginalFilename(null); }
        }

        @Override public CvSwapResult setCv(String userId, String stored, String original) {
            if (failSave) throw new IllegalStateException("db down");
            StudentProfile p = getOrCreate(userId);
            String prev = p.getCvStoredFilename();
            p.setCvStoredFilename(stored);
            p.setCvOriginalFilename(original);
            return new CvSwapResult(null, prev);
        }

        @Override public String clearCv(String userId) {
            StudentProfile p = profiles.get(userId);
            String s = p.getCvStoredFilename();
            p.setCvStoredFilename(null);
            return s;
        }

        @Override public com.skill2job.dto.StudentProfileResponse getProfile(String userId) { return null; }
    }

    private final Set<String> applications = new HashSet<>();     // "employerId|studentId"

    private JobApplicationRepository apps() {
        return (JobApplicationRepository) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{JobApplicationRepository.class},
                (proxy, m, args) -> {
                    if (m.getName().equals("existsByEmployerIdAndStudentId")) return applications.contains(args[0] + "|" + args[1]);
                    throw new UnsupportedOperationException(m.getName());
                });
    }

    private static AuthUser user(String id, Role role) {
        return new AuthUser(id, id + "@x.com", id, role);
    }

    private record Ctx(CvService svc, FakeStudents students, FileStorageService storage, Path dir) { }

    private Ctx ctx() throws IOException {
        Path dir = Files.createTempDirectory("s2j-cvsvc");
        FileStorageService storage = new FileStorageService(dir.toString(), 5);
        FakeStudents students = new FakeStudents();
        return new Ctx(new CvService(students, storage, apps()), students, storage, dir);
    }

    private static String giveCv(Ctx c, String studentId) {
        String stored = c.storage.store(new FakeUpload("cv.pdf", "application/pdf", FakeUpload.pdf()));
        c.students.setCv(studentId, stored, "cv.pdf");
        return stored;
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void studentCanReadOwnCvButNotAnotherStudents() throws IOException {
        Ctx c = ctx();
        giveCv(c, "alice");
        giveCv(c, "bob");
        assertNotNull(c.svc.get(user("alice", Role.STUDENT), "alice"));
        assertEquals(403, status(() -> c.svc.get(user("alice", Role.STUDENT), "bob")), "student must not read another student's CV");
        assertEquals(403, status(() -> c.svc.get(user("bob", Role.STUDENT), "alice")));
    }

    @Test
    void employerNeedsARealApplicationFromThatStudent() throws IOException {
        Ctx c = ctx();
        giveCv(c, "alice");
        giveCv(c, "bob");
        applications.add("emp1|alice");
        assertNotNull(c.svc.get(user("emp1", Role.EMPLOYER), "alice"));
        assertEquals(403, status(() -> c.svc.get(user("emp1", Role.EMPLOYER), "bob")), "no application from bob to emp1");
        assertEquals(403, status(() -> c.svc.get(user("emp2", Role.EMPLOYER), "alice")), "a different employer");
    }

    @Test
    void adminCanReadAnyCv() throws IOException {
        Ctx c = ctx();
        giveCv(c, "alice");
        assertNotNull(c.svc.get(user("root", Role.ADMIN), "alice"));
    }

    @Test
    void unauthorizedRequestDoesNotRevealWhetherACvExists() throws IOException {
        Ctx c = ctx();
        giveCv(c, "alice");
        // bob asks for alice (has a CV) and for carol (has none): both must look the same - 403, not 404 vs 200
        assertEquals(403, status(() -> c.svc.get(user("bob", Role.STUDENT), "alice")));
        assertEquals(403, status(() -> c.svc.get(user("bob", Role.STUDENT), "carol")));
    }

    @Test
    void readingACvNeverCreatesAProfile() throws IOException {
        Ctx c = ctx();
        assertEquals(404, status(() -> c.svc.get(user("root", Role.ADMIN), "ghost")));
        assertFalse(c.students.profiles.containsKey("ghost"));
        assertEquals(0, c.students.created);
    }

    @Test
    void missingFileClearsTheStaleReferenceAndReturns404() throws IOException {
        Ctx c = ctx();
        String stored = giveCv(c, "alice");
        Files.delete(c.dir.resolve(stored));           // e.g. the disk was wiped by a redeploy
        assertEquals(404, status(() -> c.svc.get(user("alice", Role.STUDENT), "alice")));
        assertNull(c.students.profiles.get("alice").getCvStoredFilename(), "stale reference must be cleared");
        assertEquals(404, status(() -> c.svc.get(user("alice", Role.STUDENT), "alice")));
    }

    @Test
    void staleCleanupDoesNotWipeANewerCv() throws IOException {
        Ctx c = ctx();
        String old = giveCv(c, "alice");
        String fresh = giveCv(c, "alice");
        c.students.clearCvIfStored("alice", old);       // late cleanup for the OLD name
        assertEquals(fresh, c.students.profiles.get("alice").getCvStoredFilename());
    }

    @Test
    void replacementDeletesTheOldFileOnlyAfterTheNewOneIsSaved() throws IOException {
        Ctx c = ctx();
        String old = giveCv(c, "alice");
        c.svc.upload(user("alice", Role.STUDENT), new FakeUpload("new.pdf", "application/pdf", FakeUpload.pdf()));
        String now = c.students.profiles.get("alice").getCvStoredFilename();
        assertFalse(old.equals(now));
        assertFalse(c.storage.find(old).isPresent(), "old file removed after a successful replace");
        assertTrue(c.storage.find(now).isPresent());
        assertEquals("new.pdf", c.students.profiles.get("alice").getCvOriginalFilename());
    }

    @Test
    void databaseFailureKeepsTheOldCvAndDropsTheNewFile() throws IOException {
        Ctx c = ctx();
        String old = giveCv(c, "alice");
        c.students.failSave = true;
        assertThrows(IllegalStateException.class,
                () -> c.svc.upload(user("alice", Role.STUDENT), new FakeUpload("new.pdf", "application/pdf", FakeUpload.pdf())));
        assertEquals(old, c.students.profiles.get("alice").getCvStoredFilename(), "DB still points at the old CV");
        assertTrue(c.storage.find(old).isPresent(), "the old, valid file must still exist");
        try (Stream<Path> s = Files.list(c.dir)) {
            assertEquals(1, s.count(), "the new file must not be left orphaned");
        }
    }

    @Test
    void invalidReplacementIsRejectedAndTheOldCvSurvives() throws IOException {
        Ctx c = ctx();
        String old = giveCv(c, "alice");
        assertEquals(400, status(() -> c.svc.upload(user("alice", Role.STUDENT), new FakeUpload("evil.pdf", "application/pdf", FakeUpload.text("not a pdf")))));
        assertEquals(old, c.students.profiles.get("alice").getCvStoredFilename());
        assertTrue(c.storage.find(old).isPresent());
    }

    @Test
    void onlyStudentsUploadAndDelete() throws IOException {
        Ctx c = ctx();
        assertEquals(403, status(() -> c.svc.upload(user("emp", Role.EMPLOYER), new FakeUpload("cv.pdf", "application/pdf", FakeUpload.pdf()))));
        assertEquals(403, status(() -> c.svc.remove(user("emp", Role.EMPLOYER))));
    }

    @Test
    void removeDeletesTheFile() throws IOException {
        Ctx c = ctx();
        String stored = giveCv(c, "alice");
        c.svc.remove(user("alice", Role.STUDENT));
        assertFalse(c.storage.find(stored).isPresent());
        assertNull(c.students.profiles.get("alice").getCvStoredFilename());
    }
}
