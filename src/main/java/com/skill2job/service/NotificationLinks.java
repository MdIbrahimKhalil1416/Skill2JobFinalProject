package com.skill2job.service;

import com.skill2job.enums.Role;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Where a notification leads when it is clicked. Links are built here, on the server, from ids the server already
 * trusts; the browser only ever receives them back from the API. {@link #safe(String)} is applied again when a
 * notification is read, so even a bad value stored in the database can never become an open redirect: only
 * same-site relative paths to our own pages are allowed, anything else falls back to the user's dashboard.
 */
public final class NotificationLinks {

    /** "/page.html" optionally followed by a simple query string of our own ids. No scheme, no "//", no "..". */
    private static final Pattern SAFE = Pattern.compile("^/[a-z0-9-]+\\.html(\\?[A-Za-z0-9_=&%.-]*)?$");

    private NotificationLinks() { }

    public static String job(String jobId) { return "/job.html?id=" + enc(jobId); }
    public static String chat(String applicationId) { return "/chat.html?applicationId=" + enc(applicationId); }
    /** Employer dashboard with the applicants panel of that job opened. */
    public static String applicants(String jobId) { return "/employer.html?applicants=" + enc(jobId); }
    public static String applications() { return "/applications.html"; }
    public static String interviews() { return "/interviews.html"; }
    public static String employerDashboard() { return "/employer.html"; }
    public static String jobAlerts() { return "/job-alerts.html"; }
    public static String premium() { return "/premium.html"; }
    public static String quiz() { return "/quiz.html"; }
    public static String reviews() { return "/reviews.html"; }
    public static String settings() { return "/settings.html"; }
    public static String mySkills() { return "/profile.html"; }

    /** The dashboard for a role: the safe default for notifications without a (valid) link. */
    public static String home(Role role) {
        if (role == null) return "/";
        return switch (role) {
            case STUDENT -> "/student.html";
            case EMPLOYER -> "/employer.html";
            case ADMIN -> "/admin.html";
        };
    }

    /** The link if it is a same-site relative path to one of our pages, otherwise null. */
    public static String safe(String link) {
        if (link == null || link.length() > 300 || link.contains("..") || link.contains("\\")) {
            return null;
        }
        return SAFE.matcher(link).matches() ? link : null;
    }

    /** What the client gets: the stored link when safe, otherwise the role's dashboard. Never null. */
    public static String resolve(String stored, Role role) {
        String s = safe(stored);
        return s != null ? s : home(role);
    }

    private static String enc(String id) {
        return URLEncoder.encode(id == null ? "" : id, StandardCharsets.UTF_8);
    }
}
