package com.skill2job.service;

import com.skill2job.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Stores uploaded company logos on local disk, under app.logo.upload-dir (LOGO_UPLOAD_DIR env var).
 * Mirrors FileStorageService (CVs): extension, declared Content-Type and the REAL byte count are checked, the file is
 * written under a temporary name, and it is only moved to its final generated UUID name after its content has been
 * verified to be a genuine image: PNG and JPEG are fully decoded by the JDK and size-limited in pixels, WebP gets a
 * structural check (the JDK cannot decode WebP) - see {@link FileSignatures}. The original name is never used to
 * build a path.
 *
 * Unlike a CV, a logo is meant to be public (company branding on job listings), so there is no
 * access-control service here - any public GET on the logo endpoint is allowed, see CompanyController.
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private static final Map<String, String> ALLOWED = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "webp", "image/webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/pjpeg", "image/webp", "application/octet-stream");

    private final Path rootDir;
    private final long maxSizeBytes;

    public ImageStorageService(@Value("${app.logo.upload-dir:./uploads/logos}") String uploadDir,
                               @Value("${app.logo.max-size-mb:2}") long maxSizeMb) {
        this.rootDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the logo upload directory: " + rootDir, e);
        }
        if (!Files.isWritable(rootDir)) {
            throw new IllegalStateException("The logo upload directory is not writable by the application user: " + rootDir
                    + " (set LOGO_UPLOAD_DIR to a writable folder)");
        }
    }

    /** The name to keep for display: cleaned, never used for a path. */
    public static String displayName(MultipartFile file) {
        return StorageSupport.safeDisplayName(file == null ? null : file.getOriginalFilename(), "logo");
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("The logo file is empty.");
        }
        if (file.getSize() > maxSizeBytes) {
            throw tooLarge();
        }
        String extension = StorageSupport.extensionOf(StorageSupport.safeDisplayName(file.getOriginalFilename(), ""));
        if (!ALLOWED.containsKey(extension)) {
            throw ApiException.badRequest("Only PNG, JPG or WEBP images are accepted for the logo.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.split(";")[0].trim().toLowerCase())) {
            throw ApiException.badRequest("The file does not look like a PNG, JPG or WEBP image.");
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
                throw ApiException.badRequest("The logo file is empty.");
            }
            FileSignatures.ImageType actual = FileSignatures.detectImage(temp);
            if (actual == null || actual != FileSignatures.imageTypeForExtension(extension)) {
                throw ApiException.badRequest("The file is not a real " + extension.toUpperCase()
                        + " image. Upload a PNG, JPG or WEBP image file.");
            }
            String problem = FileSignatures.imageProblem(temp, actual);
            if (problem != null) {
                throw ApiException.badRequest("The logo cannot be used: " + problem + ".");
            }
            String storedName = UUID.randomUUID() + "." + extension;
            Path target = rootDir.resolve(storedName).normalize();
            if (!rootDir.equals(target.getParent())) {
                throw ApiException.serverError("Could not store the file safely.");
            }
            FileStorageService.moveInto(temp, target);
            return storedName;
        } catch (IOException e) {
            log.error("Failed to store a logo file: {}", e.getMessage());
            throw ApiException.serverError("Could not save the uploaded logo. Please try again.");
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // swept later by the cleanup job
            }
        }
    }

    private ApiException tooLarge() {
        return ApiException.badRequest("The logo file is too large. Maximum size is " + (maxSizeBytes / (1024 * 1024)) + " MB.");
    }

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

    public Path resolve(String storedFilename) {
        return find(storedFilename).orElseThrow(() -> ApiException.notFound("Logo not found."));
    }

    public String contentTypeOf(String storedFilename) {
        return ALLOWED.getOrDefault(StorageSupport.extensionOf(storedFilename == null ? "" : storedFilename), "application/octet-stream");
    }

    /** Best-effort cleanup when a logo is replaced or an upload is rolled back. Never throws - a leftover file is harmless. */
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
            log.warn("Could not delete the old logo file {}: {}", storedFilename, e.getMessage());
        }
    }

    /** Deletes stale temp files and files older than {@code minAge} that no profile references. */
    public int sweep(Duration minAge, Predicate<String> referenced) {
        return StorageSupport.sweep(rootDir, minAge, Duration.ofHours(1), referenced);
    }
}
