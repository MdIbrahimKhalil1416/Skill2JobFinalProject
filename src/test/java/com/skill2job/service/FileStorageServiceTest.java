package com.skill2job.service;

import com.skill2job.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** CV storage against a real temporary folder: content checks, size limits, traversal, cleanup. */
class FileStorageServiceTest {

    private static Path newDir() throws IOException {
        return Files.createTempDirectory("s2j-cv-test");
    }

    private static long files(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private static void rejects(FileStorageService svc, FakeUpload up, Path dir) throws IOException {
        ApiException e = assertThrows(ApiException.class, () -> svc.store(up));
        assertEquals(400, e.getStatus().value(), "must be a 400, was: " + e.getMessage());
        assertEquals(0, files(dir), "a rejected upload must leave nothing on disk (not even a temp file)");
    }

    @Test
    void storesRealPdfDocxAndDoc() throws IOException {
        Path dir = newDir();
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        String pdf = svc.store(new FakeUpload("cv.pdf", "application/pdf", FakeUpload.pdf()));
        String docx = svc.store(new FakeUpload("cv.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", FakeUpload.docx()));
        String doc = svc.store(new FakeUpload("cv.doc", "application/msword", FakeUpload.doc()));
        assertTrue(pdf.endsWith(".pdf") && docx.endsWith(".docx") && doc.endsWith(".doc"));
        assertTrue(svc.find(pdf).isPresent() && svc.find(docx).isPresent() && svc.find(doc).isPresent());
        assertEquals(3, files(dir));
    }

    @Test
    void textFileRenamedToPdfIsRejected() throws IOException {
        Path dir = newDir();
        rejects(new FileStorageService(dir.toString(), 5), new FakeUpload("cv.pdf", "application/pdf", FakeUpload.text("just some text, not a pdf")), dir);
    }

    @Test
    void scriptAndHtmlRenamedToDocumentsAreRejected() throws IOException {
        Path dir = newDir();
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        rejects(svc, new FakeUpload("cv.docx", "application/octet-stream", FakeUpload.text("<script>alert(1)</script>")), dir);
        rejects(svc, new FakeUpload("cv.doc", "application/msword", FakeUpload.text("#!/bin/sh\nrm -rf /\n")), dir);
        rejects(svc, new FakeUpload("cv.pdf", "application/pdf", FakeUpload.text("MZ\u0090\u0000 fake exe")), dir);
    }

    @Test
    void zipThatIsNotWordIsRejectedAsDocx() throws IOException {
        Path dir = newDir();
        rejects(new FileStorageService(dir.toString(), 5), new FakeUpload("cv.docx", "application/octet-stream", FakeUpload.plainZip()), dir);
    }

    @Test
    void docxRenamedToPdfIsRejected() throws IOException {
        Path dir = newDir();
        rejects(new FileStorageService(dir.toString(), 5), new FakeUpload("cv.pdf", "application/pdf", FakeUpload.docx()), dir);
    }

    @Test
    void wrongExtensionsAndContentTypesAreRejected() throws IOException {
        Path dir = newDir();
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        rejects(svc, new FakeUpload("cv.exe", "application/octet-stream", FakeUpload.pdf()), dir);
        rejects(svc, new FakeUpload("cv.pdf.exe", "application/pdf", FakeUpload.pdf()), dir);
        rejects(svc, new FakeUpload("cv", "application/pdf", FakeUpload.pdf()), dir);
        rejects(svc, new FakeUpload("cv.pdf", "text/html", FakeUpload.pdf()), dir);
        rejects(svc, new FakeUpload("cv.svg", "image/svg+xml", FakeUpload.text("<svg/>")), dir);
    }

    @Test
    void emptyAndNullAreRejected() throws IOException {
        Path dir = newDir();
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        rejects(svc, new FakeUpload("cv.pdf", "application/pdf", new byte[0]), dir);
        assertThrows(ApiException.class, () -> svc.store(null));
    }

    @Test
    void oversizedFileIsRejectedEvenWhenTheDeclaredSizeLies() throws IOException {
        Path dir = newDir();
        FileStorageService svc = new FileStorageService(dir.toString(), 1);     // 1 MB limit
        byte[] big = new byte[2 * 1024 * 1024];
        byte[] pdf = FakeUpload.pdf();
        System.arraycopy(pdf, 0, big, 0, pdf.length);
        // honest declared size
        rejects(svc, new FakeUpload("cv.pdf", "application/pdf", big), dir);
        // declared size claims 10 bytes but the stream really is 2 MB: the real byte count must win
        rejects(svc, new FakeUpload("cv.pdf", "application/pdf", big, 10), dir);
    }

    @Test
    void pathTraversalFilenamesNeverEscapeTheUploadFolder() throws IOException {
        Path parent = Files.createTempDirectory("s2j-parent");
        Path dir = Files.createDirectory(parent.resolve("cv"));
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        for (String evil : new String[]{"../../evil.pdf", "..\\..\\evil.pdf", "/etc/passwd.pdf", "a/../../b.pdf", "C:\\Windows\\x.pdf", "..%2F..%2Fx.pdf"}) {
            String stored = svc.store(new FakeUpload(evil, "application/pdf", FakeUpload.pdf()));
            assertTrue(StorageSupport.isPlainName(stored), "stored name must be a plain generated name: " + stored);
            assertTrue(svc.find(stored).get().startsWith(dir.toAbsolutePath()), "file must live in the upload folder");
        }
        try (Stream<Path> s = Files.list(parent)) {
            assertEquals(1, s.count(), "nothing may be written outside the upload folder");
        }
    }

    @Test
    void lookupOfTraversalNamesIsRefused() throws IOException {
        Path parent = Files.createTempDirectory("s2j-parent2");
        Path dir = Files.createDirectory(parent.resolve("cv"));
        Files.write(parent.resolve("secret.pdf"), FakeUpload.pdf());
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        for (String evil : new String[]{"../secret.pdf", "..\\secret.pdf", "/etc/passwd", "sub/x.pdf", "", ".", "..", null}) {
            assertFalse(svc.find(evil).isPresent(), "must not resolve: " + evil);
        }
        svc.delete("../secret.pdf");
        assertTrue(Files.exists(parent.resolve("secret.pdf")), "delete must not reach outside the folder");
        assertThrows(ApiException.class, () -> svc.resolve("../secret.pdf"));
    }

    @Test
    void displayNameIsCleaned() {
        assertEquals("cv.pdf", FileStorageService.displayName(new FakeUpload("../../cv.pdf", "application/pdf", FakeUpload.pdf())));
        assertEquals("cv", FileStorageService.displayName(new FakeUpload(null, "application/pdf", FakeUpload.pdf())));
    }

    @Test
    void deleteIsIdempotentAndOnlyRemovesTheNamedFile() throws IOException {
        Path dir = newDir();
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        String a = svc.store(new FakeUpload("a.pdf", "application/pdf", FakeUpload.pdf()));
        String b = svc.store(new FakeUpload("b.pdf", "application/pdf", FakeUpload.pdf()));
        svc.delete(a);
        svc.delete(a);
        assertFalse(svc.find(a).isPresent());
        assertTrue(svc.find(b).isPresent());
    }

    @Test
    void sweepRemovesOnlyOldUnreferencedFilesAndStaleTempFiles() throws IOException {
        Path dir = newDir();
        FileStorageService svc = new FileStorageService(dir.toString(), 5);
        String keepReferenced = svc.store(new FakeUpload("a.pdf", "application/pdf", FakeUpload.pdf()));
        String orphanOld = svc.store(new FakeUpload("b.pdf", "application/pdf", FakeUpload.pdf()));
        String orphanYoung = svc.store(new FakeUpload("c.pdf", "application/pdf", FakeUpload.pdf()));
        Path tempOld = Files.write(dir.resolve(StorageSupport.TEMP_PREFIX + "old.tmp"), new byte[]{1});
        Path tempYoung = Files.write(dir.resolve(StorageSupport.TEMP_PREFIX + "young.tmp"), new byte[]{1});
        FileTime longAgo = FileTime.from(Instant.now().minus(Duration.ofDays(3)));
        Files.setLastModifiedTime(dir.resolve(keepReferenced), longAgo);
        Files.setLastModifiedTime(dir.resolve(orphanOld), longAgo);
        Files.setLastModifiedTime(tempOld, longAgo);

        Set<String> referenced = new HashSet<>(Arrays.asList(keepReferenced));
        int deleted = svc.sweep(Duration.ofHours(24), referenced::contains);

        assertEquals(2, deleted, "the old orphan and the old temp file");
        assertTrue(svc.find(keepReferenced).isPresent(), "a referenced file is never swept");
        assertFalse(svc.find(orphanOld).isPresent(), "an old orphan is swept");
        assertTrue(svc.find(orphanYoung).isPresent(), "a young orphan may belong to an upload still saving");
        assertFalse(Files.exists(tempOld));
        assertTrue(Files.exists(tempYoung));
    }

    @Test
    void unwritableOrInvalidDirectoryFailsFast() throws IOException {
        Path file = Files.createTempFile("s2j-notadir", ".x");
        assertThrows(IllegalStateException.class, () -> new FileStorageService(file.resolve("sub").toString(), 5));
    }
}
