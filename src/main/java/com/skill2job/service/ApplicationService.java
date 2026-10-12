package com.skill2job.service;

import com.skill2job.gamification.GamificationService;
import com.skill2job.audit.Audited;
import com.skill2job.dto.ApplicationResponse;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.JobStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.matching.MatchResult;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.security.AuthUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ApplicationService {

    private final JobApplicationRepository repository;
    private final JobService jobService;
    private final StudentService studentService;
    private final MatchingService matchingService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final MongoTemplate mongoTemplate;
    private final GamificationService gamificationService;

    public ApplicationService(JobApplicationRepository repository, JobService jobService,
                              StudentService studentService, MatchingService matchingService,
                              NotificationService notificationService, EmailService emailService,
                              MongoTemplate mongoTemplate, GamificationService gamificationService) {
        this.repository = repository;
        this.jobService = jobService;
        this.studentService = studentService;
        this.matchingService = matchingService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.mongoTemplate = mongoTemplate;
        this.gamificationService = gamificationService;
    }

    /**
     * Rules: job must be OPEN and before its deadline, no duplicate application,
     * and if a mandatory skill is missing the student must confirm with force=true.
     */
    public ApplicationResponse apply(AuthUser student, String jobId, boolean force) {
        Job job = jobService.getEntity(jobId);
        if (job.getStatus() != JobStatus.OPEN || job.getDeadline() == null
                || job.getDeadline().isBefore(JobService.today())) {
            throw ApiException.badRequest("This job is no longer accepting applications.");
        }
        if (repository.existsByJobIdAndStudentId(jobId, student.id())) {
            throw ApiException.conflict("You have already applied to this job.");
        }
        StudentProfile profile = studentService.getOrCreate(student.id());
        if (profile.getSkills().isEmpty()) {
            throw ApiException.badRequest("Add your skills to your profile before applying.");
        }
        MatchResult match = matchingService.match(profile, job);
        if (!match.eligible() && !force) {
            throw ApiException.badRequest("You are missing a mandatory skill for this job. "
                    + "You can still apply by confirming (Apply anyway).");
        }

        JobApplication a = new JobApplication();
        a.setJobId(job.getId());
        a.setJobTitle(job.getTitle());
        a.setCompanyName(job.getCompanyName());
        a.setEmployerId(job.getEmployerId());
        a.setStudentId(student.id());
        a.setStudentName(profile.getFullName() != null ? profile.getFullName() : student.name());
        a.setStudentEmail(student.email());
        a.setDepartment(profile.getDepartment());
        a.setCgpa(profile.getCgpa());
        a.setStatus(ApplicationStatus.APPLIED);
        a.setMatchPercent(match.matchPercent());
        a.setEligible(match.eligible());
        a.setAppliedAt(Instant.now());
        a.setUpdatedAt(a.getAppliedAt());
        try {
            a = repository.save(a);
        } catch (DuplicateKeyException e) {
            // Two clicks at the same time: the unique index stops the second one.
            throw ApiException.conflict("You have already applied to this job.");
        }

        notificationService.notify(job.getEmployerId(),
                a.getStudentName() + " applied for \"" + job.getTitle() + "\" (" + a.getMatchPercent() + "% match).",
                NotificationLinks.applicants(job.getId()));
        gamificationService.award(student.id(), 10, "Applied to \"" + job.getTitle() + "\"", "APPLY:" + job.getId());
        return ApplicationResponse.from(a);
    }

    public List<ApplicationResponse> mine(String studentId) {
        return repository.findByStudentIdOrderByAppliedAtDesc(studentId).stream()
                .map(ApplicationResponse::from).toList();
    }

    /** Applicants of a job, best match first. Only the job owner (or admin) can see them. */
    public List<ApplicationResponse> forJob(AuthUser user, String jobId) {
        return forJob(user, jobId, null, null, null);
    }

    /**
     * Applicants of a job, best match first, optionally filtered by minimum match %,
     * department (case-insensitive exact match) and minimum CGPA. Only the job owner (or admin).
     */
    public List<ApplicationResponse> forJob(AuthUser user, String jobId, Double minMatch, String department, Double minCgpa) {
        Job job = jobService.getEntity(jobId);
        jobService.checkOwner(user, job);

        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("jobId").is(jobId));
        if (minMatch != null) {
            parts.add(Criteria.where("matchPercent").gte(minMatch));
        }
        if (department != null && !department.isBlank()) {
            parts.add(Criteria.where("department").regex("^" + java.util.regex.Pattern.quote(department.trim()) + "$", "i"));
        }
        if (minCgpa != null) {
            parts.add(Criteria.where("cgpa").gte(minCgpa));
        }
        Query query = new Query(new Criteria().andOperator(parts.toArray(new Criteria[0])))
                .with(Sort.by(Sort.Direction.DESC, "matchPercent"));
        return mongoTemplate.find(query, JobApplication.class).stream().map(ApplicationResponse::from).toList();
    }

    @Audited(action = "APPLICATION_STATUS", targetType = "APPLICATION", targetArg = 1, detailArg = 2)
    public ApplicationResponse updateStatus(AuthUser user, String applicationId, ApplicationStatus next) {
        JobApplication a = repository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found."));
        Job job = jobService.getEntity(a.getJobId());
        jobService.checkOwner(user, job);

        if (!a.getStatus().canMoveTo(next)) {
            throw ApiException.badRequest("Cannot change status from " + a.getStatus() + " to " + next + ".");
        }
        a.setStatus(next);
        a.setUpdatedAt(Instant.now());
        a = repository.save(a);

        notificationService.notify(a.getStudentId(),
                "Your application for \"" + a.getJobTitle() + "\" at " + a.getCompanyName()
                        + " is now " + next.name() + ".", NotificationLinks.applications());
        // Best-effort: a missing/misconfigured SMTP server must never block a status change.
        emailService.sendApplicationStatusEmail(a.getStudentEmail(), a.getStudentName(), a.getJobTitle(),
                a.getCompanyName(), next);
        return ApplicationResponse.from(a);
    }

    /**
     * Bulk shortlist/reject: applies the same status to several applications at once. Each
     * application is still checked individually (ownership + allowed transition); one invalid
     * application is skipped (with a reason) rather than failing the whole batch.
     */
    @Audited(action = "APPLICATION_BULK_STATUS", targetType = "APPLICATION", targetArg = 1, detailArg = 2)
    public Map<String, Object> bulkUpdateStatus(AuthUser user, List<String> applicationIds, ApplicationStatus next) {
        List<String> updated = new ArrayList<>();
        Map<String, String> skipped = new LinkedHashMap<>();
        for (String id : applicationIds) {
            try {
                updateStatus(user, id, next);
                updated.add(id);
            } catch (ApiException e) {
                skipped.put(id, e.getMessage());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updated", updated);
        result.put("skipped", skipped);
        return result;
    }
}
