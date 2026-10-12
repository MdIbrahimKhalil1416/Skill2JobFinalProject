package com.skill2job.service;

import com.skill2job.audit.Audited;
import com.skill2job.dto.JobRequest;
import com.skill2job.dto.JobResponse;
import com.skill2job.dto.PageResponse;
import com.skill2job.dto.RequiredSkillRequest;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Job;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.entity.Skill;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.JobType;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.entity.JobApplication;
import com.skill2job.repository.ChatMessageRepository;
import com.skill2job.repository.InterviewRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.SavedJobRepository;
import com.skill2job.security.AuthUser;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class JobService {

    /** Deadlines are interpreted in Bangladesh time. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private final JobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final SkillService skillService;
    private final EmployerService employerService;
    private final MongoTemplate mongoTemplate;
    private final SavedJobRepository savedJobRepository;
    private final InterviewRepository interviewRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PaymentService paymentService;

    public JobService(JobRepository jobRepository, JobApplicationRepository applicationRepository,
                      SkillService skillService, EmployerService employerService, MongoTemplate mongoTemplate,
                      SavedJobRepository savedJobRepository, InterviewRepository interviewRepository,
                      ChatMessageRepository chatMessageRepository, PaymentService paymentService) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.skillService = skillService;
        this.employerService = employerService;
        this.mongoTemplate = mongoTemplate;
        this.savedJobRepository = savedJobRepository;
        this.interviewRepository = interviewRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.paymentService = paymentService;
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    /** Public search over OPEN jobs with optional filters, paging and sorting. */
    public PageResponse<JobResponse> search(String keyword, JobType type, String location, String skillId,
                                            String sort, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 50);

        Query query = new Query(buildCriteria(keyword, type, location, skillId));
        long total = mongoTemplate.count(query, Job.class);

        query.with(resolveSort(sort)).skip((long) safePage * safeSize).limit(safeSize);
        List<JobResponse> content = mongoTemplate.find(query, Job.class).stream().map(JobResponse::from).toList();
        return PageResponse.of(content, safePage, safeSize, total);
    }

    /** Used by the matching service to rank all open jobs for a student. */
    public List<Job> findOpenJobs(String keyword, JobType type, String location, String skillId) {
        return mongoTemplate.find(new Query(buildCriteria(keyword, type, location, skillId)), Job.class);
    }

    private Criteria buildCriteria(String keyword, JobType type, String location, String skillId) {
        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("status").is(JobStatus.OPEN.name()));
        if (keyword != null && !keyword.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(keyword.trim()), Pattern.CASE_INSENSITIVE);
            parts.add(new Criteria().orOperator(
                    Criteria.where("title").regex(p),
                    Criteria.where("description").regex(p),
                    Criteria.where("companyName").regex(p)));
        }
        if (type != null) {
            parts.add(Criteria.where("type").is(type.name()));
        }
        if (location != null && !location.isBlank()) {
            parts.add(Criteria.where("location")
                    .regex(Pattern.compile(Pattern.quote(location.trim()), Pattern.CASE_INSENSITIVE)));
        }
        if (skillId != null && !skillId.isBlank()) {
            parts.add(Criteria.where("requiredSkills.skillId").is(skillId));
        }
        return new Criteria().andOperator(parts.toArray(new Criteria[0]));
    }

    private Sort resolveSort(String sort) {
        if ("deadline".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.ASC, "deadline").and(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    public Job getEntity(String id) {
        return jobRepository.findById(id).orElseThrow(() -> ApiException.notFound("Job not found."));
    }

    public JobResponse get(String id) {
        return JobResponse.from(getEntity(id));
    }

    /** A job that is waiting for its posting fee is not published yet: only its owner and admins may see it. */
    private static boolean visibleTo(Job job, AuthUser viewer) {
        if (job.getStatus() != JobStatus.PENDING_PAYMENT) {
            return true;
        }
        return viewer != null && (viewer.role() == Role.ADMIN
                || (viewer.role() == Role.EMPLOYER && viewer.id().equals(job.getEmployerId())));
    }

    /** The job as the public / a student sees it: unpublished (unpaid) jobs of others do not exist. */
    public Job getVisibleEntity(String id, AuthUser viewer) {
        Job job = getEntity(id);
        if (!visibleTo(job, viewer)) {
            throw ApiException.notFound("Job not found.");
        }
        return job;
    }

    public JobResponse get(String id, AuthUser viewer) {
        return JobResponse.from(getVisibleEntity(id, viewer));
    }

    public List<JobResponse> employerJobs(String employerId) {
        return jobRepository.findByEmployerIdOrderByCreatedAtDesc(employerId).stream().map(JobResponse::from).toList();
    }

    @Audited(action = "JOB_CREATE", targetType = "JOB")
    public JobResponse create(AuthUser employer, JobRequest req) {
        validateDeadline(req.deadline());
        EmployerProfile profile = employerService.getOrCreate(employer.id());

        boolean needsPayment = paymentService.jobPostingRequiresPayment(profile);

        Job job = new Job();
        job.setEmployerId(employer.id());
        job.setCompanyName(profile.getCompanyName());
        job.setStatus(needsPayment ? JobStatus.PENDING_PAYMENT : JobStatus.OPEN);
        job.setCreatedAt(Instant.now());
        if (!needsPayment) {
            job.setPublishedAt(job.getCreatedAt());
        }
        applyRequest(job, req);
        Job saved = jobRepository.save(job);
        // A persistent counter, never derived from a live count of current jobs, so deleting a job
        // later can never refund the free-post slot it used (see EmployerProfile.jobsPostedCount).
        employerService.incrementJobsPosted(profile);
        return JobResponse.from(saved);
    }

    public JobResponse update(AuthUser user, String id, JobRequest req) {
        Job job = getEntity(id);
        checkOwner(user, job);
        validateDeadline(req.deadline());
        applyRequest(job, req);
        return JobResponse.from(jobRepository.save(job));
    }

    @Audited(action = "JOB_CLOSE", targetType = "JOB", targetArg = 1)
    public JobResponse close(AuthUser user, String id) {
        Job job = getEntity(id);
        checkOwner(user, job);
        if (job.getStatus() == JobStatus.PENDING_PAYMENT) {
            // Closing would turn "unpaid" into "closed", and reopening a closed job needs no payment.
            throw ApiException.badRequest("This job is waiting for its posting fee. Pay to publish it, or delete it.");
        }
        job.setStatus(JobStatus.CLOSED);
        return JobResponse.from(jobRepository.save(job));
    }

    @Audited(action = "JOB_REOPEN", targetType = "JOB", targetArg = 1)
    public JobResponse reopen(AuthUser user, String id) {
        Job job = getEntity(id);
        checkOwner(user, job);
        if (job.getStatus() == JobStatus.PENDING_PAYMENT) {
            throw ApiException.badRequest("Complete the payment to publish this job first.");
        }
        if (job.getDeadline() == null || job.getDeadline().isBefore(today())) {
            throw ApiException.badRequest("The deadline has passed. Edit the job and set a new deadline first.");
        }
        job.setStatus(JobStatus.OPEN);
        return JobResponse.from(jobRepository.save(job));
    }

    /** Owner or admin. Applications, bookmarks, interviews and chat messages of the job are deleted too. */
    @Audited(action = "JOB_DELETE", targetType = "JOB", targetArg = 1)
    public void delete(AuthUser user, String id) {
        Job job = getEntity(id);
        checkOwner(user, job);
        List<String> applicationIds = applicationRepository.findByJobIdOrderByMatchPercentDesc(job.getId()).stream()
                .map(JobApplication::getId).toList();
        if (!applicationIds.isEmpty()) {
            chatMessageRepository.deleteByApplicationIdIn(applicationIds);
        }
        interviewRepository.deleteByJobId(job.getId());
        applicationRepository.deleteByJobId(job.getId());
        savedJobRepository.deleteByJobId(job.getId());
        jobRepository.delete(job);
    }

    /** Looks up several jobs at once (side-by-side comparison). Unknown ids are silently skipped. */
    public List<JobResponse> getMany(List<String> ids, AuthUser viewer) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> distinct = ids.stream().distinct().limit(4).toList();
        return jobRepository.findAllById(distinct).stream()
                .filter(j -> visibleTo(j, viewer)).map(JobResponse::from).toList();
    }

    /** Employers can only touch their own jobs. Admin can touch any job. */
    public void checkOwner(AuthUser user, Job job) {
        if (user.role() == Role.ADMIN) {
            return;
        }
        if (user.role() != Role.EMPLOYER || !job.getEmployerId().equals(user.id())) {
            throw ApiException.forbidden("You can only manage your own jobs.");
        }
    }

    private void validateDeadline(LocalDate deadline) {
        if (deadline == null || deadline.isBefore(today())) {
            throw ApiException.badRequest("Deadline must be today or a future date.");
        }
        if (deadline.isAfter(today().plusYears(1))) {
            throw ApiException.badRequest("Deadline must be within one year.");
        }
    }

    private void applyRequest(Job job, JobRequest req) {
        Set<String> seen = new HashSet<>();
        for (RequiredSkillRequest rs : req.requiredSkills()) {
            if (!seen.add(rs.skillId())) {
                throw ApiException.badRequest("The same skill was added twice.");
            }
        }
        Map<String, Skill> skills = skillService.getMapByIds(seen);
        List<RequiredSkill> required = new ArrayList<>();
        for (RequiredSkillRequest rs : req.requiredSkills()) {
            Skill skill = skills.get(rs.skillId());
            if (skill == null) {
                throw ApiException.badRequest("Unknown skill id: " + rs.skillId());
            }
            required.add(new RequiredSkill(skill.getId(), skill.getName(), rs.requiredLevel(), rs.weight(), rs.mandatory()));
        }
        job.setTitle(req.title().trim());
        job.setDescription(req.description().trim());
        job.setType(req.type());
        job.setLocation(req.location().trim());
        job.setDeadline(req.deadline());
        job.setRequiredSkills(required);
    }
}
