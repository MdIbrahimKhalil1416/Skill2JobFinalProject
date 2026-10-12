package com.skill2job.service;

import com.skill2job.enums.ApplicationStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sends every outbound email (signup OTP, password reset, application status changes, deadline reminders,
 * interview notices, job alerts). Two transports, chosen automatically:
 * <ul>
 *   <li><b>Brevo HTTPS API</b> when BREVO_API_KEY is set (production on Render: Render's free web services block
 *       outbound SMTP ports, so SMTP cannot work there). See {@link BrevoEmailClient}.</li>
 *   <li>Otherwise <b>SMTP</b> through Spring's JavaMailSender with MAIL_USERNAME / MAIL_PASSWORD (local development).</li>
 * </ul>
 * The sender address is app.mail.from (MAIL_FROM, defaulting to MAIL_USERNAME); with Brevo it must be a sender
 * verified in the Brevo dashboard.
 *
 * Sending is always best-effort: a grader/demo environment may not have real SMTP credentials
 * configured, and a missing or failing mail server must never leave a user permanently stuck mid
 * signup or block an employer's workflow. On any failure this logs the problem and returns false;
 * the triggering action (signup, status change, reminder) always still completes, and (only when
 * app.otp.log-to-console is enabled, which is off in the "prod" profile) the OTP is also printed
 * to the server log so that one flow can still be tested end-to-end without real SMTP.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.otp.log-to-console:false}")
    private boolean logOtpToConsole;

    @Value("${app.mail.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${app.mail.brevo.url:" + BrevoEmailClient.DEFAULT_URL + "}")
    private String brevoUrl;

    @Value("${app.mail.from-name:Skill2Job}")
    private String fromName;

    /** Created on first use (the @Value fields are only set after construction). Tests may replace it. */
    private BrevoEmailClient brevo;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Dev convenience: with no email provider configured and app.otp.log-to-console=true, the code is in the server
     * log, which counts as "delivered" for the signup flow. Always false in production (the guard forbids the flag). */
    public boolean devConsoleFallback() {
        return logOtpToConsole && !emailConfigured();
    }

    /** True when Brevo (BREVO_API_KEY) or SMTP (MAIL_USERNAME) is configured. */
    public boolean emailConfigured() {
        return brevoEnabled() || (mailUsername != null && !mailUsername.isBlank());
    }

    /** Brevo is used whenever an API key is configured; no separate provider switch is needed. */
    boolean brevoEnabled() {
        return brevoApiKey != null && !brevoApiKey.isBlank();
    }

    private synchronized BrevoEmailClient brevo() {
        if (brevo == null) {
            brevo = new BrevoEmailClient(brevoApiKey, brevoUrl, null);
        }
        return brevo;
    }

    /** Test seam: route Brevo calls through a fake transport (no network). */
    synchronized void useBrevoTransport(BrevoEmailClient.Transport transport) {
        this.brevo = new BrevoEmailClient(brevoApiKey, brevoUrl, transport);
    }

    public boolean sendOtpEmail(String toEmail, String recipientName, String otp, int expiryMinutes) {
        if (logOtpToConsole) {
            log.info("[DEV ONLY] OTP for {} is {} (valid {} minute(s)). "
                    + "This is only logged because app.otp.log-to-console=true.", toEmail, otp, expiryMinutes);
        }
        String body = "Hi " + (recipientName == null || recipientName.isBlank() ? "there" : recipientName) + ",\n\n"
                + "Your Skill2Job verification code is: " + otp + "\n\n"
                + "This code expires in " + expiryMinutes + " minute(s). "
                + "If you did not try to sign up, you can safely ignore this email.\n\n"
                + "- Skill2Job";
        return send(toEmail, "Your Skill2Job verification code", body);
    }

    /**
     * Sent whenever an application's status changes. HIRED and REJECTED use a dedicated, friendly
     * template (feature: auto-generated acceptance/rejection email); SHORTLISTED gets a shorter
     * heads-up. (APPLIED is the initial state and never reaches this method.)
     */
    public boolean sendApplicationStatusEmail(String toEmail, String studentName, String jobTitle,
                                              String companyName, ApplicationStatus status) {
        String name = (studentName == null || studentName.isBlank()) ? "there" : studentName;
        String subject;
        String body;
        switch (status) {
            case HIRED -> {
                subject = "Congratulations! You got the offer for " + jobTitle;
                body = "Hi " + name + ",\n\n"
                        + "Great news - " + companyName + " would like to move forward and hire you for the \""
                        + jobTitle + "\" position you applied to on Skill2Job.\n\n"
                        + "The employer will reach out with next steps (contract, start date, onboarding). "
                        + "Congratulations on this achievement!\n\n- Skill2Job";
            }
            case REJECTED -> {
                subject = "Update on your application for " + jobTitle;
                body = "Hi " + name + ",\n\n"
                        + "Thank you for applying to \"" + jobTitle + "\" at " + companyName + " through Skill2Job. "
                        + "After careful consideration, the employer has decided not to move forward with your "
                        + "application this time.\n\n"
                        + "This is rarely about one factor alone, and we encourage you to keep building your skills "
                        + "and apply to other roles that match your profile.\n\n- Skill2Job";
            }
            case SHORTLISTED -> {
                subject = "You've been shortlisted for " + jobTitle;
                body = "Hi " + name + ",\n\n"
                        + companyName + " has shortlisted your application for \"" + jobTitle + "\". "
                        + "They may reach out to schedule an interview - keep an eye on your Skill2Job notifications "
                        + "and this inbox.\n\n- Skill2Job";
            }
            default -> {
                subject = "Update on your application for " + jobTitle;
                body = "Hi " + name + ",\n\nYour application for \"" + jobTitle + "\" at " + companyName
                        + " is now " + status.name() + ".\n\n- Skill2Job";
            }
        }
        return send(toEmail, subject, body);
    }

    /** "Deadline in N day(s) for a job you bookmarked" reminder email. */
    public boolean sendDeadlineReminderEmail(String toEmail, String studentName, String jobTitle, String companyName,
                                             LocalDate deadline) {
        String name = (studentName == null || studentName.isBlank()) ? "there" : studentName;
        String when = deadline == null ? "soon" : deadline.format(DateTimeFormatter.ofPattern("d MMM yyyy"));
        String subject = "Reminder: deadline approaching for " + jobTitle;
        String body = "Hi " + name + ",\n\n"
                + "The application deadline for \"" + jobTitle + "\" at " + companyName
                + " (a job you bookmarked on Skill2Job) is " + when + ". "
                + "Apply now if you are still interested.\n\n- Skill2Job";
        return send(toEmail, subject, body);
    }

    /** Sent to an applicant when an employer proposes interview time slots. */
    public boolean sendInterviewProposedEmail(String toEmail, String studentName, String jobTitle, String companyName) {
        String name = (studentName == null || studentName.isBlank()) ? "there" : studentName;
        String subject = "Interview slots proposed for " + jobTitle;
        String body = "Hi " + name + ",\n\n"
                + companyName + " has proposed interview time slots for \"" + jobTitle + "\". "
                + "Log in to Skill2Job to pick the slot that works best for you.\n\n- Skill2Job";
        return send(toEmail, subject, body);
    }

    /** Sent to an employer once the applicant has picked an interview slot. */
    public boolean sendInterviewSelectedEmail(String toEmail, String studentName, String jobTitle, String selectedSlot) {
        String subject = "Interview slot confirmed for " + jobTitle;
        String body = "Hi,\n\n" + studentName + " has selected the interview slot " + selectedSlot
                + " for \"" + jobTitle + "\".\n\n- Skill2Job";
        return send(toEmail, subject, body);
    }

    /** Digest of new jobs matching one of a student's saved job alerts. */
    public boolean sendJobAlertEmail(String toEmail, String studentName, String alertName, List<String> jobLines, String jobsUrl) {
        String name = (studentName == null || studentName.isBlank()) ? "there" : studentName;
        String subject = jobLines.size() == 1
                ? "New job for your alert \"" + alertName + "\""
                : jobLines.size() + " new jobs for your alert \"" + alertName + "\"";
        StringBuilder body = new StringBuilder("Hi " + name + ",\n\nNew jobs matching your Skill2Job alert \""
                + alertName + "\":\n\n");
        for (String line : jobLines) {
            body.append(" - ").append(line).append('\n');
        }
        body.append("\nSee them and your match %: ").append(jobsUrl)
                .append("\n\nYou can pause or delete this alert any time from the Job alerts page.\n\n- Skill2Job");
        return send(toEmail, subject, body.toString());
    }

    /** The 6-digit "forgot password" code. Also logged to the console in dev, like the signup OTP. */
    public boolean sendPasswordResetEmail(String toEmail, String name, String code, int expiryMinutes) {
        if (logOtpToConsole) {
            log.info("[DEV ONLY] Password reset code for {} is {} (valid {} minute(s)).", toEmail, code, expiryMinutes);
        }
        String who = (name == null || name.isBlank()) ? "there" : name;
        String body = "Hi " + who + ",\n\nYour Skill2Job password reset code is: " + code + "\n\n"
                + "It expires in " + expiryMinutes + " minute(s). If you did not ask to reset your password, "
                + "ignore this email - your password stays the same.\n\n- Skill2Job";
        return send(toEmail, "Your Skill2Job password reset code", body);
    }

    /** Security notice after any password change or reset. */
    public boolean sendPasswordChangedEmail(String toEmail, String name) {
        String who = (name == null || name.isBlank()) ? "there" : name;
        String body = "Hi " + who + ",\n\nThe password of your Skill2Job account was just changed. "
                + "All other signed-in sessions have been signed out.\n\n"
                + "If this was not you, reset your password immediately using \"Forgot password\" on the login page "
                + "and contact the administrator.\n\n- Skill2Job";
        return send(toEmail, "Your Skill2Job password was changed", body);
    }

    /** The one place that delivers mail: Brevo when configured, otherwise SMTP. Never throws; never logs the body. */
    private boolean send(String toEmail, String subject, String body) {
        if (brevoEnabled()) {
            return brevo().send(fromAddress, fromName, toEmail, subject, body);
        }
        if (mailUsername == null || mailUsername.isBlank()) {
            log.warn("No email provider is configured (set BREVO_API_KEY, or MAIL_USERNAME/MAIL_PASSWORD for SMTP): "
                    + "the email to {} (\"{}\") was not sent.", toEmail, subject);
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(toEmail);
            helper.setFrom(fromAddress);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            return true;
        } catch (MessagingException | RuntimeException e) {
            log.error("Failed to send email to {} (\"{}\"): {}", toEmail, subject, e.getMessage());
            return false;
        }
    }
}
