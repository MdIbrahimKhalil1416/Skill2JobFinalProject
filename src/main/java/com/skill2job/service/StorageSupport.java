package com.skill2job.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Small, dependency-free helpers shared by FileStorageService (CVs) and ImageStorageService (logos). */
public final class StorageSupport {

    /** Files being uploaded live under this prefix until they are validated and renamed; they are never served. */
    public static final String TEMP_PREFIX = ".upload-";

    private StorageSupport() { }

    /** A stored file name we generated ourselves: no separators, no dots at the start, no "..", not blank. */
    public static boolean isPlainName(String name) {
        return name != null && !name.isBlank() && !name.startsWith(".") && !name.contains("/") && !name.contains("\\")
                && !name.contains("..") && name.indexOf('\0') < 0 && name.indexOf(':') < 0;   // ":" = Windows drive-relative path
    }

    public static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * A filename that is safe to store for display and to put in a download header: only the last path segment,
     * no control characters, no characters Windows/HTTP treat specially, no leading dots, at most 100 characters
     * (the extension is kept). Never used to build a path on disk.
     */
    public static String safeDisplayName(String original, String fallback) {
        if (original == null) {
            return fallback;
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || "<>:\"|?*;%".indexOf(c) >= 0) {
                continue;
            }
            sb.append(c);
        }
        name = sb.toString().strip();
        while (name.startsWith(".")) {
            name = name.substring(1).strip();
        }
        if (name.isEmpty()) {
            return fallback;
        }
        if (name.length() > 100) {
            String ext = extensionOf(name);
            String stem = name.substring(0, name.length() - (ext.isEmpty() ? 0 : ext.length() + 1));
            int keep = 100 - (ext.isEmpty() ? 0 : ext.length() + 1);
            name = stem.substring(0, Math.max(1, Math.min(stem.length(), keep))) + (ext.isEmpty() ? "" : "." + ext);
        }
        return name;
    }

    /**
     * Copies at most {@code maxBytes} from {@code in} to {@code target} (created/overwritten).
     * @return the number of bytes written, or -1 if the stream holds MORE than maxBytes (the declared size of a
     *         multipart upload is not trusted - the real bytes are counted).
     */
    public static long copyLimited(InputStream in, Path target, long maxBytes) throws IOException {
        long total = 0;
        byte[] buf = new byte[16 * 1024];
        try (OutputStream out = Files.newOutputStream(target)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) {
                    return -1;
                }
                out.write(buf, 0, n);
            }
        }
        return total;
    }

    /**
     * Deletes files in {@code root} that nothing references any more: stale upload temp files older than
     * {@code tempMinAge}, and regular files older than {@code minAge} for which {@code referenced} says no.
     * Young files are never touched (an upload may still be saving its database row). Failures are ignored -
     * a leftover file is harmless.
     * @return how many files were deleted
     */
    public static int sweep(Path root, Duration minAge, Duration tempMinAge, Predicate<String> referenced) {
        int deleted = 0;
        Instant now = Instant.now();
        try (Stream<Path> files = Files.list(root)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                try {
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    String name = p.getFileName().toString();
                    Instant modified = Files.getLastModifiedTime(p).toInstant();
                    boolean temp = name.startsWith(TEMP_PREFIX);
                    if (temp ? modified.isBefore(now.minus(tempMinAge))
                            : modified.isBefore(now.minus(minAge)) && !referenced.test(name)) {
                        Files.deleteIfExists(p);
                        deleted++;
                    }
                } catch (IOException ignored) {
                    // leave it for the next run
                }
            }
        } catch (IOException e) {
            return deleted;
        }
        return deleted;
    }
}
