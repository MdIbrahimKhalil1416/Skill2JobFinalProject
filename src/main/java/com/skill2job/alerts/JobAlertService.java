package com.skill2job.alerts;

import com.skill2job.service.NotificationLinks;
import com.skill2job.dto.JobMatchResponse;
import com.skill2job.dto.JobResponse;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobAlert;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.matching.MatchCalculator;
import com.skill2job.matching.MatchResult;
import com.skill2job.repository.JobAlertRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.service.EmailService;
import com.skill2job.service.JobService;
import com.skill2job.service.NotificationService;
import com.skill2job.service.SkillService;
import com.skill2job.service.StudentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Saved job searches ("job alerts"). JobAlertScheduler calls runAll() periodically; every active
 * alert gets one digest (in-app notification + optional email) listing jobs PUBLISHED since its last
 * check that match its filters and, optionally, the student's minimum match %.
 */
@Service
public class JobAlertService {

    private static final Logger log = LoggerFactory.getLogger(JobAlertService.class);
    public static final int MAX_ALERTS = 10;
    /** Student Pro raises the cap. */
    public static final int MAX_ALERTS_PRO = 30;

    public static int maxAlerts(boolean pro) {
        return pro ? MAX_ALERTS_PRO : MAX_ALERTS;
    }

    private final JobAlertRepository alertRepository;
    private final JobService jobService;
    private final StudentService studentService;
    private final SkillService skillService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public JobAlertService(JobAlertRepository alertRepository, JobService jobService, StudentService studentService,
                           SkillService skillService, NotificationService notificationService, EmailService emailService,
                           UserRepository userRepository, GamificationService gamificationService) {
        this.alertRepository = alertRepository;
        this.jobService = jobService;
        this.studentService = studentService;
        this.skillService = skillService;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.gamificationService = gamificationService;
    }

    public List<JobAlertDtos.AlertResponse> mine(String studentId) {
        return alertRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream().map(JobAlertService::toResponse).toList();
    }

    public JobAlertDtos.AlertResponse create(String studentId, JobAlertDtos.AlertRequest req) {
        int cap = maxAlerts(studentService.getOrCreate(studentId).isProActive());
        if (alertRepository.countByStudentId(studentId) >= cap) {
            throw ApiException.badRequest("You can have at most " + cap + " job alerts"
                    + (cap == MAX_ALERTS ? " (Student Pro raises this to " + MAX_ALERTS_PRO + ")" : "") + ". Delete one first.");
        }
        JobAlert alert = new JobAlert();
        alert.setStudentId(studentId);
        alert.setCreatedAt(Instant.now());
        // Only jobs published from now on are "new" for this alert; preview shows the existing ones.
        alert.setLastCheckedAt(Instant.now());
        apply(alert, req);
        JobAlert saved = alertRepository.save(alert);
        gamificationService.award(studentId, 5, "Created your first job alert", "ALERT_FIRST");
        return toResponse(saved);
    }

    public JobAlertDtos.AlertResponse update(String studentId, String id, JobAlertDtos.AlertRequest req) {
        JobAlert alert = own(studentId, id);
        apply(alert, req);
        return toResponse(alertRepository.save(alert));
    }

    public void delete(String studentId, String id) {
        alertRepository.delete(own(studentId, id));
    }

    /** Jobs that match the alert right now (not only new ones), best match first. */
    public List<JobMatchResponse> preview(String studentId, String id) {
        JobAlert alert = own(studentId, id);
        StudentProfile profile = studentService.getOrCreate(studentId);
        return matching(alert, StudentService.levelMap(profile), null, null).stream().limit(20).toList();
    }

    /** "Check now" button: runs this student's active alerts immediately. */
    public JobAlertDtos.RunResult runForStudent(String studentId) {
        int total = 0;
        int checked = 0;
        for (JobAlert a : alertRepository.findByStudentIdOrderByCreatedAtDesc(studentId)) {
            if (a.isActive()) {
                checked++;
                total += run(a);
            }
        }
        return new JobAlertDtos.RunResult(checked, total);
    }

    /** Called by JobAlertScheduler. One failing alert never stops the others. */
    public JobAlertDtos.RunResult runAll() {
        int total = 0;
        int checked = 0;
        for (JobAlert a : alertRepository.findByActiveTrue()) {
            try {
                checked++;
                total += run(a);
            } catch (RuntimeException e) {
                log.warn("Job alert {} failed: {}", a.getId(), e.getMessage());
            }
        }
        if (total > 0) {
            log.info("Job alerts: {} alert(s) checked, {} new job match(es) sent", checked, total);
        }
        return new JobAlertDtos.RunResult(checked, total);
    }

    /** Sends one digest for the jobs published since the alert's last check; returns how many. */
    int run(JobAlert alert) {
        Instant since = alert.getLastCheckedAt() == null ? alert.getCreatedAt() : alert.getLastCheckedAt();
        Instant now = Instant.now();
        StudentProfile profile = studentService.getOrCreate(alert.getStudentId());
        List<JobMatchResponse> fresh = matching(alert, StudentService.levelMap(profile), since, now);
        alert.setLastCheckedAt(now);
        if (!fresh.isEmpty()) {
            alert.setLastNotifiedAt(now);
            alert.setTotalJobsSent(alert.getTotalJobsSent() + fresh.size());
            List<String> lines = fresh.stream().limit(10)
                    .map(m -> m.job().title() + " at " + m.job().companyName() + " - " + m.matchPercent() + "% match")
                    .toList();
            String preview = String.join(", ", fresh.stream().limit(3).map(m -> m.job().title()).toList());
            notificationService.notify(alert.getStudentId(), "Job alert \"" + alert.getName() + "\": "
                    + fresh.size() + " new job(s) - " + preview + (fresh.size() > 3 ? "..." : ""),
                    fresh.size() == 1 ? NotificationLinks.job(fresh.get(0).job().id()) : NotificationLinks.jobAlerts());
            if (alert.isEmailEnabled()) {
                userRepository.findById(alert.getStudentId()).ifPresent(u ->
                        emailService.sendJobAlertEmail(u.getEmail(), u.getName(), alert.getName(), lines,
                                baseUrl + "/jobs.html"));
            }
        }
        alertRepository.save(alert);
        return fresh.size();
    }

    private List<JobMatchResponse> matching(JobAlert alert, Map<String, Integer> levels, Instant publishedAfter,
                                           Instant publishedUntil) {
        List<JobMatchResponse> out = new ArrayList<>();
        for (Job job : jobService.findOpenJobs(alert.getKeyword(), alert.getType(), alert.getLocation(), alert.getSkillId())) {
            Instant published = job.getPublishedAt() != null ? job.getPublishedAt() : job.getCreatedAt();
            if (publishedAfter != null && (published == null || !published.isAfter(publishedAfter))) {
                continue;
            }
            // (since, now] window: a job published while this check runs is left for the next run,
            // so no job is ever sent twice or skipped.
            if (publishedUntil != null && published != null && published.isAfter(publishedUntil)) {
                continue;
            }
            MatchResult r = MatchCalculator.calculate(levels, job.getRequiredSkills());
            if (alert.getMinMatch() != null && r.matchPercent() < alert.getMinMatch()) {
                continue;
            }
            out.add(new JobMatchResponse(JobResponse.from(job), r.matchPercent(), r.eligible(), r.missing().size()));
        }
        out.sort(Comparator.comparingDouble(JobMatchResponse::matchPercent).reversed());
        return out;
    }

    private void apply(JobAlert alert, JobAlertDtos.AlertRequest req) {
        String keyword = blankToNull(req.keyword());
        String location = blankToNull(req.location());
        String skillId = blankToNull(req.skillId());
        if (keyword == null && req.type() == null && location == null && skillId == null
                && (req.minMatch() == null || req.minMatch() == 0)) {
            throw ApiException.badRequest("Add at least one filter (keyword, type, location, skill or minimum match).");
        }
        alert.setName(req.name().trim());
        alert.setKeyword(keyword);
        alert.setType(req.type());
        alert.setLocation(location);
        if (skillId != null) {
            Skill skill = skillService.getEntity(skillId);
            alert.setSkillId(skill.getId());
            alert.setSkillName(skill.getName());
        } else {
            alert.setSkillId(null);
            alert.setSkillName(null);
        }
        alert.setMinMatch(req.minMatch() == null || req.minMatch() == 0 ? null : req.minMatch());
        alert.setEmailEnabled(req.emailEnabled());
        if (req.active() != null) {
            alert.setActive(req.active());
        }
    }

    private JobAlert own(String studentId, String id) {
        return alertRepository.findById(id).filter(a -> a.getStudentId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("Job alert not found."));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static JobAlertDtos.AlertResponse toResponse(JobAlert a) {
        return new JobAlertDtos.AlertResponse(a.getId(), a.getName(), a.getKeyword(), a.getType(), a.getLocation(),
                a.getSkillId(), a.getSkillName(), a.getMinMatch(), a.isEmailEnabled(), a.isActive(), a.getCreatedAt(),
                a.getLastNotifiedAt(), a.getTotalJobsSent());
    }
}
