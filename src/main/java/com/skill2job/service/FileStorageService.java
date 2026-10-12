package com.skill2job.service;

import com.skill2job.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Stores uploaded CVs on local disk, under app.cv.upload-dir (CV_UPLOAD_DIR env var).
 *
 * A file is accepted only if (1) its extension is pdf/doc/docx, (2) its declared Content-Type (when sent) is a
 * document type, (3) its REAL size - counted while copying, the declared size is not trusted - is within the limit
 * and not zero, and (4) its actual content is a PDF / Word document (see {@link FileSignatures} for what that does
 * and does not prove). It is first written to a temporary name, validated there, and only then moved to its final
 * name, so a rejected upload never leaves a usable file behind.
 *
 * The original filename the user picked is never used to build a path: every file is written under a freshly
 * generated UUID name, which is what rules out path traversal ("../../etc/passwd") and one user overwriting another
 * user's file by guessing a name. The original name is kept separately (StudentProfile.cvOriginalFilename), cleaned
 * by {@link #displayName}, purely for display and for the Content-Disposition header on download.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            // Some browsers/OSes send a generic type for .doc/.docx; the extension and content checks still apply.
            "application/octet-stream");

    private final Path rootDir;
    private final long maxSizeBytes;

    public FileStorageService(@Value("${app.cv.upload-dir:./uploads/cv}") String uploadDir,
                              @Value("${app.cv.max-size-mb:5}") long maxSizeMb) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the CV upload directory: " + rootDir, e);
        }
        if (!Files.isWritable(rootDir)) {
            throw new IllegalStateException("The CV upload directory is not writable by the application user: " + rootDir
                    + " (set CV_UPLOAD_DIR to a writable folder)");
        }
    }

    /** The name to keep for display/download: cleaned, never used for a path. */
    public static String displayName(MultipartFile file) {
        return StorageSupport.safeDisplayName(file == null ? null : file.getOriginalFilename(), "cv");
    }

    /** Validates type, size and content, writes the file under a new generated name, and returns that name. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The CV file is empty.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw tooLarge();
        }
        String claimed = StorageSupport.extensionOf(StorageSupport.safeDisplayName(file.getOriginalFilename(), ""));
        if (!ALLOWED_EXTENSIONS.contains(claimed)) {
            throw ApiException.badRequest("Only PDF, DOC or DOCX files are accepted for the CV.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.split(";")[0].trim().toLowerCase())) {
            throw ApiException.badRequest("The file does not look like a PDF, DOC or DOCX document.");
        }

        Path temp = rootDir.resolve(StorageSupport.TEMP_PREFIX + UUID.randomUUID() + ".tmp").normalize();
        if (!rootDir.equals(temp.getParent())) {
            throw ApiException.serverError("Could not store the file safely.");
        }
        try {
            long written;
            try (var in = file.getInputStream()) {
                written = StorageSupport.copyLimited(in, temp, maxSizeBytes);
            }
            if (written < 0) {
                throw tooLarge();
            }
            if (written == 0) {
                throw ApiException.badRequest("The CV file is empty.");
            }
            FileSignatures.CvType actual = FileSignatures.detectCv(temp);
            if (actual == null || (claimed.equals("pdf") != (actual == FileSignatures.CvType.PDF))) {
                // Not a document at all, or a PDF/Word mix-up. (doc vs docx is tolerated: it is stored as what it really is.)
                throw ApiException.badRequest("The file is not a valid PDF, DOC or DOCX document. "
                        + "Check that it opens correctly and upload it again.");
            }
            String storedName = UUID.randomUUID() + "." + actual.extension;
            Path target = rootDir.resolve(storedName).normalize();
            if (!rootDir.equals(target.getParent())) {
                throw ApiException.serverError("Could not store the file safely.");
            }
            moveInto(temp, target);
            return storedName;
        } catch (IOException e) {
            log.error("Failed to store a CV file: {}", e.getMessage());
            throw ApiException.serverError("Could not save the uploaded CV. Please try again.");
        } finally {
            try {
                Files.deleteIfExists(temp);   // no-op after a successful move
            } catch (IOException ignored) {
                // swept later by the cleanup job
            }
        }
    }

    private ApiException tooLarge() {
        return ApiException.badRequest("The CV file is too large. Maximum size is " + (maxSizeBytes / (1024 * 1024)) + " MB.");
    }

    static void moveInto(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target);
        }
    }

    /** The file for a stored name we generated, or empty when the name is not one of ours or the file is gone. */
    public Optional<Path> find(String storedFilename) {
        if (!StorageSupport.isPlainName(storedFilename)) {
            return Optional.empty();
        }
        Path path = rootDir.resolve(storedFilename).normalize();
        if (!rootDir.equals(path.getParent()) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    /** Resolves a previously stored name back to a path. Rejects anything that is not a plain name we generated. */
    public Path resolve(String storedFilename) {
        return find(storedFilename).orElseThrow(() -> ApiException.notFound("CV not found."));
    }

    /** Best-effort cleanup when a CV is replaced or an upload is rolled back. Never throws - a leftover file is harmless. */
    public void delete(String storedFilename) {
        if (!StorageSupport.isPlainName(storedFilename)) {
            return;
        }
        Path path = rootDir.resolve(storedFilename).normalize();
        if (!rootDir.equals(path.getParent())) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete the old CV file {}: {}", storedFilename, e.getMessage());
        }
    }

    /** Deletes stale temp files and files older than {@code minAge} that no profile references. */
    public int sweep(Duration minAge, Predicate<String> referenced) {
        return StorageSupport.sweep(rootDir, minAge, Duration.ofHours(1), referenced);
    }
}
