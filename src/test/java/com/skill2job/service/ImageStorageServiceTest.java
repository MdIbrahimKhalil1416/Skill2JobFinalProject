package com.skill2job.service;

import com.skill2job.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Logo storage: every image is really decoded, not trusted by its extension. */
class ImageStorageServiceTest {

    private static long files(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private static void rejects(ImageStorageService svc, FakeUpload up, Path dir) throws IOException {
        ApiException e = assertThrows(ApiException.class, () -> svc.store(up));
        assertEquals(400, e.getStatus().value(), "must be a 400, was: " + e.getMessage());
        assertEquals(0, files(dir), "a rejected logo must leave nothing on disk");
    }

    @Test
    void storesRealPngAndJpegAndReportsTheRightContentType() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        String png = svc.store(new FakeUpload("logo.png", "image/png", FakeUpload.image("png", 40, 30)));
        String jpg = svc.store(new FakeUpload("logo.jpg", "image/jpeg", FakeUpload.image("jpg", 40, 30)));
        assertEquals("image/png", svc.contentTypeOf(png));
        assertEquals("image/jpeg", svc.contentTypeOf(jpg));
        assertTrue(svc.find(png).isPresent() && svc.find(jpg).isPresent());
    }

    @Test
    void textOrScriptRenamedToImageIsRejected() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        rejects(svc, new FakeUpload("logo.png", "image/png", FakeUpload.text("not an image at all")), dir);
        rejects(svc, new FakeUpload("logo.png", "image/png", FakeUpload.text("<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>")), dir);
        rejects(svc, new FakeUpload("logo.jpg", "image/jpeg", FakeUpload.text("<html><script>1</script></html>")), dir);
    }

    @Test
    void imageWithTheWrongExtensionIsRejected() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        rejects(svc, new FakeUpload("logo.jpg", "image/jpeg", FakeUpload.image("png", 20, 20)), dir);
        rejects(svc, new FakeUpload("logo.png", "image/png", FakeUpload.image("jpg", 20, 20)), dir);
    }

    @Test
    void truncatedPngWithValidHeaderIsRejected() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        byte[] full = FakeUpload.image("png", 200, 200);
        byte[] cut = Arrays.copyOf(full, 40);        // signature + IHDR, but no pixel data
        rejects(svc, new FakeUpload("logo.png", "image/png", cut), dir);
    }

    @Test
    void absurdPixelDimensionsAreRejected() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        rejects(svc, new FakeUpload("logo.png", "image/png", FakeUpload.image("png", FileSignatures.MAX_IMAGE_DIMENSION + 1, 10)), dir);
    }

    @Test
    void fakeWebpIsRejected() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        rejects(svc, new FakeUpload("logo.webp", "image/webp", FakeUpload.text("this is not a webp file")), dir);
    }

    @Test
    void wrongTypeEmptyAndOversizedAreRejected() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 1);
        byte[] png = FakeUpload.image("png", 20, 20);
        rejects(svc, new FakeUpload("logo.gif", "image/gif", png), dir);
        rejects(svc, new FakeUpload("logo.svg", "image/svg+xml", FakeUpload.text("<svg/>")), dir);
        rejects(svc, new FakeUpload("logo.png", "text/html", png), dir);
        rejects(svc, new FakeUpload("logo.png", "image/png", new byte[0]), dir);
        byte[] big = new byte[2 * 1024 * 1024];
        System.arraycopy(png, 0, big, 0, png.length);
        rejects(svc, new FakeUpload("logo.png", "image/png", big), dir);
        rejects(svc, new FakeUpload("logo.png", "image/png", big, 5), dir);   // lying declared size
    }

    @Test
    void pathTraversalNamesStayInsideTheFolder() throws IOException {
        Path parent = Files.createTempDirectory("s2j-parent3");
        Path dir = Files.createDirectory(parent.resolve("logos"));
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        String stored = svc.store(new FakeUpload("../../x.png", "image/png", FakeUpload.image("png", 10, 10)));
        assertTrue(StorageSupport.isPlainName(stored));
        assertTrue(svc.find(stored).get().startsWith(dir.toAbsolutePath()));
        assertFalse(svc.find("../x.png").isPresent());
        assertFalse(svc.find("/etc/passwd").isPresent());
        try (Stream<Path> s = Files.list(parent)) {
            assertEquals(1, s.count());
        }
    }

    @Test
    void sweepKeepsReferencedLogos() throws IOException {
        Path dir = Files.createTempDirectory("s2j-logo-test");
        ImageStorageService svc = new ImageStorageService(dir.toString(), 2);
        String used = svc.store(new FakeUpload("a.png", "image/png", FakeUpload.image("png", 10, 10)));
        String orphan = svc.store(new FakeUpload("b.png", "image/png", FakeUpload.image("png", 10, 10)));
        var old = java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(java.time.Duration.ofDays(2)));
        Files.setLastModifiedTime(dir.resolve(used), old);
        Files.setLastModifiedTime(dir.resolve(orphan), old);
        assertEquals(1, svc.sweep(java.time.Duration.ofHours(24), used::equals));
        assertTrue(svc.find(used).isPresent());
        assertFalse(svc.find(orphan).isPresent());
    }
}
