package com.skill2job.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageSupportTest {

    @Test
    void plainNames() {
        assertTrue(StorageSupport.isPlainName("0f8c1c1e-1111-2222-3333-444455556666.pdf"));
        for (String bad : new String[]{null, "", ".", "..", "../a.pdf", "a/b.pdf", "a\\b.pdf", "/etc/passwd", "a\0b.pdf", "C:x.pdf"}) {
            assertFalse(StorageSupport.isPlainName(bad), "should not be plain: " + bad);
        }
    }

    @Test
    void safeDisplayName() {
        assertEquals("cv.pdf", StorageSupport.safeDisplayName("..\\..\\cv.pdf", "x"));
        assertEquals("cv.pdf", StorageSupport.safeDisplayName("/tmp/cv.pdf", "x"));
        assertEquals("x", StorageSupport.safeDisplayName("", "x"));
        assertEquals("x", StorageSupport.safeDisplayName(null, "x"));
        assertEquals("x", StorageSupport.safeDisplayName("...", "x"));
        assertEquals("cv.pdf", StorageSupport.safeDisplayName(".hidden\n<cv>.pdf".replace(".hidden", ""), "x"));
        assertFalse(StorageSupport.safeDisplayName("a\r\nb.pdf", "x").contains("\n"));
        String longName = "a".repeat(500) + ".pdf";
        assertTrue(StorageSupport.safeDisplayName(longName, "x").length() <= 100);
        assertTrue(StorageSupport.safeDisplayName(longName, "x").endsWith(".pdf"), "extension survives truncation");
    }

    @Test
    void extensionOf() {
        assertEquals("pdf", StorageSupport.extensionOf("CV.PDF"));
        assertEquals("", StorageSupport.extensionOf("cv"));
        assertEquals("exe", StorageSupport.extensionOf("cv.pdf.exe"));
    }
}
