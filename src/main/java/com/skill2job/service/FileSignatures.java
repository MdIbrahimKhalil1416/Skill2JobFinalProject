package com.skill2job.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Looks at what an uploaded file ACTUALLY is, not at what its name or Content-Type claims.
 *
 * What this can and cannot establish (be honest about the limits):
 *  - PDF: the "%PDF-" header must be there. A file that merely starts with that header but is otherwise junk, or a
 *    PDF carrying malicious JavaScript, is NOT detected - that needs a full PDF parser/antivirus.
 *  - DOCX: it must be a real ZIP whose directory contains "[Content_Types].xml" and a "word/..." part, with sane
 *    entry counts/sizes (zip-bomb guard). It is not unpacked or scanned for macros.
 *  - DOC: it must be an OLE2 compound file that contains a "WordDocument" stream name. Old binary Word files can
 *    still hold macros; they are served only as a download, never rendered by this server.
 *  - Images: PNG and JPEG are fully DECODED by the JDK (so a text file renamed .png fails); WebP has no decoder in
 *    the JDK, so only its RIFF/VP8 structure, declared size and canvas size are checked.
 */
public final class FileSignatures {

    public enum CvType {
        PDF("pdf"), DOC("doc"), DOCX("docx");
        public final String extension;
        CvType(String extension) { this.extension = extension; }
    }

    public enum ImageType { PNG, JPEG, WEBP }

    /** Largest logo edge accepted; keeps a decoded image (4 bytes per pixel) around 36 MB at the very most. */
    public static final int MAX_IMAGE_DIMENSION = 3000;

    private static final int MAX_ZIP_ENTRIES = 5000;
    private static final long MAX_ZIP_UNCOMPRESSED = 300L * 1024 * 1024;
    private static final byte[] WORD_STREAM = "WordDocument".getBytes(StandardCharsets.UTF_16LE);

    private FileSignatures() { }

    private static byte[] head(Path file, int n) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            byte[] buf = new byte[(int) Math.min(n, raf.length())];
            raf.readFully(buf);
            return buf;
        }
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    // ------------------------------------------------------------ CV documents

    /** @return the detected document type, or null when the file is none of PDF / DOC / DOCX. */
    public static CvType detectCv(Path file) throws IOException {
        byte[] head = head(file, 1024);
        if (indexOf(head, "%PDF-".getBytes(StandardCharsets.US_ASCII)) >= 0) {
            return CvType.PDF;
        }
        if (startsWith(head, 'P', 'K', 3, 4)) {
            return isDocx(file) ? CvType.DOCX : null;
        }
        if (startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
            return indexOf(Files.readAllBytes(file), WORD_STREAM) >= 0 ? CvType.DOC : null;
        }
        return null;
    }

    private static boolean isDocx(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            boolean contentTypes = false;
            boolean wordPart = false;
            int count = 0;
            long uncompressed = 0;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (++count > MAX_ZIP_ENTRIES) {
                    return false;
                }
                if (e.getSize() > 0) {
                    uncompressed += e.getSize();
                    if (uncompressed > MAX_ZIP_UNCOMPRESSED) {
                        return false;
                    }
                }
                String name = e.getName();
                if (name.contains("..") || name.startsWith("/")) {
                    return false;
                }
                contentTypes |= name.equals("[Content_Types].xml");
                wordPart |= name.startsWith("word/");
            }
            return contentTypes && wordPart;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ images

    /** @return the image format named by the file's first bytes, or null. */
    public static ImageType detectImage(Path file) throws IOException {
        byte[] h = head(file, 16);
        if (startsWith(h, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return ImageType.PNG;
        }
        if (startsWith(h, 0xFF, 0xD8, 0xFF)) {
            return ImageType.JPEG;
        }
        if (h.length >= 12 && startsWith(h, 'R', 'I', 'F', 'F') && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return ImageType.WEBP;
        }
        return null;
    }

    /** The image formats a file extension may legitimately hold. */
    public static ImageType imageTypeForExtension(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "png" -> ImageType.PNG;
            case "jpg", "jpeg" -> ImageType.JPEG;
            case "webp" -> ImageType.WEBP;
            default -> null;
        };
    }

    /** @return null when the file is a complete, readable image of the given type within the size limits, otherwise why not. */
    public static String imageProblem(Path file, ImageType type) throws IOException {
        if (type == ImageType.WEBP) {
            return webpProblem(Files.readAllBytes(file));
        }
        ImageIO.setUseCache(false);
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                return "the file is not a readable image";
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return "the file is not a valid PNG or JPEG image";
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w < 1 || h < 1) {
                    return "the image has no size";
                }
                if (w > MAX_IMAGE_DIMENSION || h > MAX_IMAGE_DIMENSION) {
                    return "the image is too large (" + w + "x" + h + " pixels; at most " + MAX_IMAGE_DIMENSION + "x" + MAX_IMAGE_DIMENSION + ")";
                }
                if (reader.read(0) == null) {   // full decode: truncated or corrupt files fail here
                    return "the image could not be decoded";
                }
                return null;
            } catch (IOException | RuntimeException e) {
                return "the image is corrupt, incomplete or in an unsupported format (for JPEG use RGB, not CMYK)";
            } finally {
                reader.dispose();
            }
        }
    }

    private static String webpProblem(byte[] d) {
        if (d.length < 30) {
            return "the WebP file is too small to be valid";
        }
        long riff = (d[4] & 0xFFL) | (d[5] & 0xFFL) << 8 | (d[6] & 0xFFL) << 16 | (d[7] & 0xFFL) << 24;
        if (riff < 22 || d.length < riff + 8) {
            return "the WebP file is incomplete";
        }
        String chunk = new String(d, 12, 4, StandardCharsets.US_ASCII);
        if (!chunk.equals("VP8 ") && !chunk.equals("VP8L") && !chunk.equals("VP8X")) {
            return "the WebP file has an unknown format";
        }
        if (chunk.equals("VP8X")) {
            int w = 1 + ((d[24] & 0xFF) | (d[25] & 0xFF) << 8 | (d[26] & 0xFF) << 16);
            int h = 1 + ((d[27] & 0xFF) | (d[28] & 0xFF) << 8 | (d[29] & 0xFF) << 16);
            if (w > MAX_IMAGE_DIMENSION || h > MAX_IMAGE_DIMENSION) {
                return "the image is too large (" + w + "x" + h + " pixels; at most " + MAX_IMAGE_DIMENSION + "x" + MAX_IMAGE_DIMENSION + ")";
            }
        }
        return null;
    }
}
