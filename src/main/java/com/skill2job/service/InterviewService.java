package com.skill2job.service;

import com.skill2job.dto.InterviewResponse;
import com.skill2job.entity.Interview;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.InterviewStatus;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.InterviewRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** Employer proposes interview time slots for an application; the student picks the one that works. */
@Service
public class InterviewService {

    private final InterviewRepository repository;
    private final JobApplicationRepository applicationRepository;
    private final JobService jobService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public InterviewService(InterviewRepository repository, JobApplicationRepository applicationRepository,
                            JobService jobService, NotificationService notificationService,
                            EmailService emailService) {
        this.repository = repository;
        this.applicationRepository = applicationRepository;
        this.jobService = jobService;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    public InterviewResponse propose(AuthUser employer, String applicationId, List<String> slots) {
        JobApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        Job job = jobService.getEntity(app.getJobId());
        jobService.checkOwner(employer, job);
        if (app.getStatus() == ApplicationStatus.REJECTED) {
            throw ApiException.badRequest("You cannot schedule an interview for a rejected application.");
        }

        Interview interview = new Interview();
        interview.setApplicationId(app.getId());
        interview.setJobId(job.getId());
        interview.setJobTitle(job.getTitle());
        interview.setCompanyName(job.getCompanyName());
        interview.setEmployerId(job.getEmployerId());
        interview.setStudentId(app.getStudentId());
        interview.setStudentName(app.getStudentName());
        interview.setStudentEmail(app.getStudentEmail());
        interview.setProposedSlots(List.copyOf(slots));
        interview.setStatus(InterviewStatus.PROPOSED);
        interview.setCreatedAt(Instant.now());
        interview.setUpdatedAt(interview.getCreatedAt());
        interview = repository.save(interview);

        notificationService.notify(app.getStudentId(),
                job.getCompanyName() + " proposed interview time slots for \"" + job.getTitle() + "\". Pick one.",
                NotificationLinks.interviews());
        emailService.sendInterviewProposedEmail(app.getStudentEmail(), app.getStudentName(), job.getTitle(), job.getCompanyName());
        return InterviewResponse.from(interview);
    }

    public InterviewResponse selectSlot(AuthUser student, String interviewId, String slot) {
        Interview interview = get(interviewId);
        if (!interview.getStudentId().equals(student.id())) {
            throw ApiException.forbidden("This interview is not yours.");
        }
        if (interview.getStatus() != InterviewStatus.PROPOSED) {
            throw ApiException.badRequest("This interview is no longer awaiting a slot selection.");
        }
        if (!interview.getProposedSlots().contains(slot)) {
            throw ApiException.badRequest("Pick one of the proposed slots.");
        }
        interview.setSelectedSlot(slot);
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview.setUpdatedAt(Instant.now());
        interview = repository.save(interview);

        notificationService.notify(interview.getEmployerId(),
                interview.getStudentName() + " selected the interview slot " + slot + " for \""
                        + interview.getJobTitle() + "\".", NotificationLinks.interviews());
        return InterviewResponse.from(interview);
    }

    public InterviewResponse cancel(AuthUser user, String interviewId) {
        Interview interview = get(interviewId);
        Job job = jobService.getEntity(interview.getJobId());
        jobService.checkOwner(user, job);
        interview.setStatus(InterviewStatus.CANCELLED);
        interview.setUpdatedAt(Instant.now());
        interview = repository.save(interview);
        notificationService.notify(interview.getStudentId(),
                "The interview for \"" + interview.getJobTitle() + "\" at " + interview.getCompanyName() + " was cancelled.",
                NotificationLinks.interviews());
        return InterviewResponse.from(interview);
    }

    public List<InterviewResponse> mine(AuthUser user) {
        List<Interview> list = user.role() == Role.STUDENT
                ? repository.findByStudentIdOrderByCreatedAtDesc(user.id())
                : repository.findByEmployerIdOrderByCreatedAtDesc(user.id());
        return list.stream().map(InterviewResponse::from).toList();
    }

    private Interview get(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Interview not found."));
    }
}
