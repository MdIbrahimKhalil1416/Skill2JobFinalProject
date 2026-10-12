package com.skill2job.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DownloadHeadersTest {

    @Test
    void plainNameIsQuotedAndEncoded() {
        assertEquals("attachment; filename=\"cv.pdf\"; filename*=UTF-8''cv.pdf", DownloadHeaders.attachment("cv.pdf"));
    }

    @Test
    void headerInjectionAttemptsCannotBreakOutOfTheHeader() {
        String h = DownloadHeaders.attachment("a\"; filename=\"evil.exe\r\nSet-Cookie: x=1\r\n.pdf");
        assertFalse(h.contains("\r") || h.contains("\n"), "no line breaks in a header");
        assertEquals(1, h.split("filename=\"", -1).length - 1, "exactly one quoted filename, no injected second one");
        assertFalse(h.contains("Set-Cookie: "), "header text must not survive intact");
    }

    @Test
    void nonAsciiNamesGetAnAsciiFallbackAndAPercentEncodedRealName() {
        String h = DownloadHeaders.attachment("সিভি résumé.pdf");
        String ascii = h.substring(h.indexOf("filename=\"") + 10, h.indexOf("\";"));
        for (char c : ascii.toCharArray()) {
            assertTrue(c >= 0x20 && c < 0x7F, "fallback must be printable ASCII");
        }
        assertTrue(h.contains("filename*=UTF-8''"));
        assertTrue(h.contains("%C3%A9"), "é must be percent-encoded as UTF-8");
        assertFalse(h.substring(h.indexOf("filename*=")).contains(" "), "encoded name has no raw spaces");
    }

    @Test
    void pathsAndMissingNamesAreReduced() {
        assertTrue(DownloadHeaders.attachment("../../etc/passwd").contains("filename=\"passwd\""));
        assertTrue(DownloadHeaders.attachment(null).contains("filename=\"download\""));
        assertTrue(DownloadHeaders.attachment("   ").contains("filename=\"download\""));
    }
}
