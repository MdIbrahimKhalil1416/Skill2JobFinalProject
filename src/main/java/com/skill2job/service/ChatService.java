package com.skill2job.service;

import com.skill2job.dto.ChatMessageResponse;
import com.skill2job.entity.ChatMessage;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.ChatMessageRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * In-app chat between an employer and an applicant, scoped to one job application.
 * Only opens once the application has been shortlisted (or hired); a rejected or still-just-applied
 * application has no chat, matching the requested feature ("employer and shortlisted applicant").
 */
@Service
public class ChatService {

    private final ChatMessageRepository repository;
    private final JobApplicationRepository applicationRepository;
    private final JobService jobService;
    private final NotificationService notificationService;

    public ChatService(ChatMessageRepository repository, JobApplicationRepository applicationRepository,
                       JobService jobService, NotificationService notificationService) {
        this.repository = repository;
        this.applicationRepository = applicationRepository;
        this.jobService = jobService;
        this.notificationService = notificationService;
    }

    public List<ChatMessageResponse> list(AuthUser user, String applicationId) {
        JobApplication app = getAccessible(user, applicationId);
        if (app.getStatus() == ApplicationStatus.APPLIED) {
            throw ApiException.badRequest("Chat opens once the application has been shortlisted.");
        }
        return repository.findByApplicationIdOrderBySentAtAsc(applicationId).stream()
                .map(ChatMessageResponse::from).toList();
    }

    public ChatMessageResponse send(AuthUser user, String applicationId, String text) {
        JobApplication app = getAccessible(user, applicationId);
        if (app.getStatus() == ApplicationStatus.APPLIED || app.getStatus() == ApplicationStatus.REJECTED) {
            throw ApiException.badRequest("You can only message a shortlisted or hired applicant.");
        }
        ChatMessage m = new ChatMessage();
        m.setApplicationId(applicationId);
        m.setSenderId(user.id());
        m.setSenderRole(user.role());
        m.setSenderName(user.name());
        m.setText(text.trim());
        m.setSentAt(Instant.now());
        m = repository.save(m);

        String recipientId = user.role() == Role.STUDENT ? app.getEmployerId() : app.getStudentId();
        notificationService.notify(recipientId,
                user.name() + " sent you a message about \"" + app.getJobTitle() + "\".", NotificationLinks.chat(app.getId()));
        return ChatMessageResponse.from(m);
    }

    /** Admin, the application's own student, or the job's owning employer. */
    private JobApplication getAccessible(AuthUser user, String applicationId) {
        JobApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        if (user.role() == Role.ADMIN) {
            return app;
        }
        if (user.role() == Role.STUDENT) {
            if (!app.getStudentId().equals(user.id())) {
                throw ApiException.forbidden("This conversation is not yours.");
            }
            return app;
        }
        Job job = jobService.getEntity(app.getJobId());
        jobService.checkOwner(user, job);
        return app;
    }
}
