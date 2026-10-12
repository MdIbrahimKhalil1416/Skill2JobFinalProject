package com.skill2job.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;

/** A MultipartFile for tests, plus builders for small but REAL sample documents and images. */
final class FakeUpload implements MultipartFile {

    private final String filename;
    private final String contentType;
    private final byte[] data;
    private final long declaredSize;

    FakeUpload(String filename, String contentType, byte[] data) {
        this(filename, contentType, data, data.length);
    }

    FakeUpload(String filename, String contentType, byte[] data, long declaredSize) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
        this.declaredSize = declaredSize;
    }

    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return filename; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return declaredSize == 0; }
    @Override public long getSize() { return declaredSize; }
    @Override public byte[] getBytes() { return data; }
    @Override public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(data); }
    @Override public void transferTo(File dest) throws IOException, IllegalStateException { }

    // ---- sample content -------------------------------------------------------------------------------

    static byte[] pdf() {
        return "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] docx() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(bos)) {
                put(z, "[Content_Types].xml", "<Types/>");
                put(z, "_rels/.rels", "<Relationships/>");
                put(z, "word/document.xml", "<w:document/>");
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A zip that is NOT a Word document (e.g. someone renamed archive.zip to cv.docx). */
    static byte[] plainZip() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream z = new ZipOutputStream(bos)) {
                put(z, "readme.txt", "hello");
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] doc() {
        byte[] head = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
        byte[] word = "WordDocument".getBytes(StandardCharsets.UTF_16LE);
        byte[] out = new byte[600];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(word, 0, out, 128, word.length);
        return out;
    }

    static byte[] image(String format, int w, int h) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, format, bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void put(ZipOutputStream z, String name, String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }
}
