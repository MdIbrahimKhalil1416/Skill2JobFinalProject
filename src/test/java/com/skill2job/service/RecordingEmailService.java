package com.skill2job.service;

import java.util.ArrayList;
import java.util.List;

/** EmailService double: records what would have been sent and can simulate SMTP failure. No mail is ever sent. */
class RecordingEmailService extends EmailService {

    record Sent(String to, String kind, String code) { }

    final List<Sent> sent = new ArrayList<>();
    boolean failing;
    boolean throwing;
    boolean devFallback;

    RecordingEmailService() { super(null); }

    private synchronized boolean record(String to, String kind, String code) {
        if (throwing) throw new IllegalStateException("smtp exploded");
        if (failing) return false;
        sent.add(new Sent(to, kind, code));
        return true;
    }

    @Override public boolean sendOtpEmail(String toEmail, String name, String otp, int expiryMinutes) { return record(toEmail, "otp", otp); }
    @Override public boolean sendPasswordResetEmail(String toEmail, String name, String code, int expiryMinutes) { return record(toEmail, "reset", code); }
    @Override public boolean sendPasswordChangedEmail(String toEmail, String name) { return record(toEmail, "changed", null); }
    @Override public boolean sendApplicationStatusEmail(String to, String name, String jobTitle, String company,
                                                         com.skill2job.enums.ApplicationStatus status) {
        return record(to, "status:" + status, null);
    }
    @Override public boolean sendInterviewProposedEmail(String to, String name, String jobTitle, String company) {
        return record(to, "interview-proposed", null);
    }
    @Override public boolean devConsoleFallback() { return devFallback; }

    String lastCode(String to, String kind) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            Sent s = sent.get(i);
            if (s.to().equalsIgnoreCase(to) && s.kind().equals(kind)) return s.code();
        }
        return null;
    }

    long count(String kind) { return sent.stream().filter(s -> s.kind().equals(kind)).count(); }
}
