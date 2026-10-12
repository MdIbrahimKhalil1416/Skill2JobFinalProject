package com.skill2job.service;

import java.nio.charset.StandardCharsets;

/**
 * Builds a safe {@code Content-Disposition: attachment} value (RFC 6266 / RFC 5987). The original file name
 * is user-controlled, so it is never pasted into the header raw: the plain {@code filename=} is reduced to
 * printable ASCII without quotes/backslashes/CR/LF (so it can never break out of the header), and the real
 * name travels percent-encoded in {@code filename*=UTF-8''...}.
 */
public final class DownloadHeaders {

    private DownloadHeaders() { }

    public static String attachment(String name) {
        String safe = StorageSupport.safeDisplayName(name, "download");
        StringBuilder ascii = new StringBuilder();
        for (char c : safe.toCharArray()) {
            ascii.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' && c != '%' ? c : '_');
        }
        return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + percentEncode(safe);
    }

    static String percentEncode(String s) {
        StringBuilder out = new StringBuilder();
        for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean unreserved = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~';
            if (unreserved) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }
}
