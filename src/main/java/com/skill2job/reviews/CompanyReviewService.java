package com.skill2job.reviews;

import com.skill2job.service.NotificationLinks;
import com.skill2job.audit.Audited;
import com.skill2job.entity.CompanyReview;
import com.skill2job.entity.Interview;
import com.skill2job.entity.JobApplication;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.ExperienceType;
import com.skill2job.enums.ReviewStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.CompanyReviewRepository;
import com.skill2job.repository.EmployerProfileRepository;
import com.skill2job.repository.InterviewRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.service.NotificationService;
import com.skill2job.service.StudentService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Company reviews & ratings. A student may review an employer only after a real experience with it
 * (an application the employer responded to, or an interview). Every new or edited review waits in
 * the admin moderation queue; only APPROVED reviews are public and count towards the rating.
 */
@Service
public class CompanyReviewService {

    private final CompanyReviewRepository reviewRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final StudentService studentService;
    private final NotificationService notificationService;
    private final GamificationService gamificationService;

    public CompanyReviewService(CompanyReviewRepository reviewRepository, JobApplicationRepository applicationRepository,
                                InterviewRepository interviewRepository, EmployerProfileRepository employerProfileRepository,
                                StudentService studentService, NotificationService notificationService,
                                GamificationService gamificationService) {
        this.reviewRepository = reviewRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.employerProfileRepository = employerProfileRepository;
        this.studentService = studentService;
        this.notificationService = notificationService;
        this.gamificationService = gamificationService;
    }

    public record RatingSummary(double average, long count, List<Long> distribution) { }

    /** Average of APPROVED reviews (one decimal) and how many reviews gave 1..5 stars. */
    public RatingSummary summary(String employerId) {
        return summarize(reviewRepository.findByEmployerIdAndStatusOrderByCreatedAtDesc(employerId, ReviewStatus.APPROVED));
    }

    static RatingSummary summarize(List<CompanyReview> approved) {
        List<Long> dist = new ArrayList<>(List.of(0L, 0L, 0L, 0L, 0L));
        for (CompanyReview r : approved) {
            int idx = Math.max(1, Math.min(5, r.getRating())) - 1;
            dist.set(idx, dist.get(idx) + 1);
        }
        double avg = approved.stream().mapToInt(CompanyReview::getRating).average().orElse(0);
        return new RatingSummary(Math.round(avg * 10.0) / 10.0, approved.size(), dist);
    }

    public List<CompanyReviewDtos.PublicReview> publicReviews(String employerId) {
        return reviewRepository.findByEmployerIdAndStatusOrderByCreatedAtDesc(employerId, ReviewStatus.APPROVED).stream()
                .map(CompanyReviewService::toPublic).toList();
    }

    /** Recent, positive approved reviews for the landing page's success-stories section. */
    public List<CompanyReviewDtos.PublicReview> featured() {
        return reviewRepository.findTop12ByStatusAndRatingGreaterThanEqualOrderByCreatedAtDesc(ReviewStatus.APPROVED, 4).stream()
                .map(CompanyReviewService::toPublic).toList();
    }

    // ------------------------------------------------------------------ student

    public List<CompanyReviewDtos.Reviewable> reviewable(String studentId) {
        Map<String, String> companyNames = new LinkedHashMap<>();
        Map<String, Set<String>> titles = new LinkedHashMap<>();
        Map<String, ExperienceType> types = new LinkedHashMap<>();
        for (JobApplication a : applicationRepository.findByStudentIdOrderByAppliedAtDesc(studentId)) {
            if (a.getStatus() == ApplicationStatus.APPLIED || a.getEmployerId() == null) {
                continue;
            }
            companyNames.putIfAbsent(a.getEmployerId(), a.getCompanyName());
            titles.computeIfAbsent(a.getEmployerId(), k -> new LinkedHashSet<>()).add(a.getJobTitle());
            ExperienceType t = a.getStatus() == ApplicationStatus.HIRED ? ExperienceType.JOB : ExperienceType.APPLICATION;
            types.merge(a.getEmployerId(), t, (x, y) -> x == ExperienceType.JOB || y == ExperienceType.JOB ? ExperienceType.JOB : x);
        }
        for (Interview i : interviewRepository.findByStudentIdOrderByCreatedAtDesc(studentId)) {
            if (i.getEmployerId() == null) {
                continue;
            }
            companyNames.putIfAbsent(i.getEmployerId(), i.getCompanyName());
            titles.computeIfAbsent(i.getEmployerId(), k -> new LinkedHashSet<>()).add(i.getJobTitle());
            types.merge(i.getEmployerId(), ExperienceType.INTERVIEW, (x, y) -> x == ExperienceType.JOB ? x : y);
        }
        Map<String, CompanyReview> mine = new LinkedHashMap<>();
        reviewRepository.findByStudentIdOrderByCreatedAtDesc(studentId).forEach(r -> mine.put(r.getEmployerId(), r));
        List<CompanyReviewDtos.Reviewable> list = new ArrayList<>();
        companyNames.forEach((employerId, name) -> list.add(new CompanyReviewDtos.Reviewable(employerId,
                companyName(employerId, name), new ArrayList<>(titles.getOrDefault(employerId, Set.of())),
                types.getOrDefault(employerId, ExperienceType.APPLICATION),
                mine.containsKey(employerId) ? toFull(mine.get(employerId)) : null)));
        return list;
    }

    public CompanyReviewDtos.FullReview upsert(String studentId, String employerId, CompanyReviewDtos.ReviewRequest req) {
        if (!eligible(studentId, employerId)) {
            throw ApiException.forbidden("You can review a company after it has responded to your application or interviewed you.");
        }
        if (isBlank(req.pros()) && isBlank(req.cons())) {
            throw ApiException.badRequest("Write at least something in Pros or Cons.");
        }
        CompanyReview r = reviewRepository.findByEmployerIdAndStudentId(employerId, studentId).orElseGet(() -> {
            CompanyReview n = new CompanyReview();
            n.setEmployerId(employerId);
            n.setStudentId(studentId);
            n.setCreatedAt(Instant.now());
            return n;
        });
        r.setCompanyName(companyName(employerId, null));
        r.setStudentName(studentService.getOrCreate(studentId).getFullName());
        r.setAnonymous(req.anonymous());
        r.setRating(req.rating());
        r.setTitle(req.title().trim());
        r.setPros(trim(req.pros()));
        r.setCons(trim(req.cons()));
        r.setExperienceType(req.experienceType());
        r.setJobTitle(trim(req.jobTitle()));
        r.setStatus(ReviewStatus.PENDING);
        r.setModerationNote(null);
        r.setModeratedAt(null);
        r.setUpdatedAt(Instant.now());
        return toFull(reviewRepository.save(r));
    }

    public void deleteMine(String studentId, String reviewId) {
        CompanyReview r = reviewRepository.findById(reviewId).filter(x -> x.getStudentId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("Review not found."));
        reviewRepository.delete(r);
    }

    boolean eligible(String studentId, String employerId) {
        boolean responded = applicationRepository.findByStudentIdAndEmployerId(studentId, employerId).stream()
                .anyMatch(a -> a.getStatus() != ApplicationStatus.APPLIED);
        return responded || interviewRepository.existsByStudentIdAndEmployerId(studentId, employerId);
    }

    // ------------------------------------------------------------------ admin

    public List<CompanyReviewDtos.FullReview> forAdmin(ReviewStatus status) {
        List<CompanyReview> list = status == null
                ? reviewRepository.findTop50ByOrderByCreatedAtDesc()
                : reviewRepository.findByStatusOrderByCreatedAtAsc(status);
        return list.stream().map(CompanyReviewService::toFull).toList();
    }

    public long pendingCount() {
        return reviewRepository.countByStatus(ReviewStatus.PENDING);
    }

    @Audited(action = "REVIEW_MODERATE", targetType = "COMPANY_REVIEW", targetArg = 0, detailArg = 1)
    public CompanyReviewDtos.FullReview moderate(String reviewId, CompanyReviewDtos.ModerationRequest req) {
        if (req.status() == ReviewStatus.PENDING) {
            throw ApiException.badRequest("Choose APPROVED or REJECTED.");
        }
        CompanyReview r = reviewRepository.findById(reviewId).orElseThrow(() -> ApiException.notFound("Review not found."));
        r.setStatus(req.status());
        r.setModerationNote(trim(req.note()));
        r.setModeratedAt(Instant.now());
        reviewRepository.save(r);
        if (req.status() == ReviewStatus.APPROVED) {
            notificationService.notify(r.getStudentId(), "Your review of " + r.getCompanyName() + " is now public. Thank you!",
                    NotificationLinks.reviews());
            gamificationService.award(r.getStudentId(), 10, "Review of " + r.getCompanyName() + " approved", "REVIEW_APPROVED:" + r.getId());
        } else {
            notificationService.notify(r.getStudentId(), "Your review of " + r.getCompanyName() + " was not published"
                    + (r.getModerationNote() == null ? "." : ": " + r.getModerationNote()), NotificationLinks.reviews());
        }
        return toFull(r);
    }

    private String companyName(String employerId, String fallback) {
        return employerProfileRepository.findByUserId(employerId).map(p -> p.getCompanyName())
                .orElse(fallback == null ? "Company" : fallback);
    }

    private static CompanyReviewDtos.PublicReview toPublic(CompanyReview r) {
        return new CompanyReviewDtos.PublicReview(r.getId(), r.getEmployerId(), r.getCompanyName(),
                r.isAnonymous() || r.getStudentName() == null ? "Anonymous student" : shortName(r.getStudentName()),
                r.getRating(), r.getTitle(), r.getPros(), r.getCons(), r.getExperienceType(), r.getJobTitle(), r.getCreatedAt());
    }

    static CompanyReviewDtos.FullReview toFull(CompanyReview r) {
        return new CompanyReviewDtos.FullReview(r.getId(), r.getEmployerId(), r.getCompanyName(), r.getStudentId(),
                r.getStudentName(), r.isAnonymous(), r.getRating(), r.getTitle(), r.getPros(), r.getCons(),
                r.getExperienceType(), r.getJobTitle(), r.getStatus(), r.getModerationNote(), r.getCreatedAt(),
                r.getUpdatedAt(), r.getModeratedAt());
    }

    /** "Nusrat Jahan" -> "Nusrat J." on public pages. */
    static String shortName(String full) {
        String[] parts = full.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
