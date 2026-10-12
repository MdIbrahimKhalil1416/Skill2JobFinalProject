package com.skill2job.analytics;

import com.skill2job.dto.CountItem;
import com.skill2job.entity.Interview;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.InterviewStatus;
import com.skill2job.enums.JobStatus;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.InterviewRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.reviews.CompanyReviewService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employer analytics dashboard: hiring funnel (applied -> reviewed -> shortlisted -> interviewed ->
 * hired), time to hire, applications over time, applicant skills, the skills applicants most often
 * lack, match-% distribution and a per-job table. Optionally narrowed to one job.
 */
@Service
public class EmployerAnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private final JobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final StudentProfileRepository profileRepository;
    private final CompanyReviewService reviewService;

    public EmployerAnalyticsService(JobRepository jobRepository, JobApplicationRepository applicationRepository,
                                    InterviewRepository interviewRepository, StudentProfileRepository profileRepository,
                                    CompanyReviewService reviewService) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.profileRepository = profileRepository;
        this.reviewService = reviewService;
    }

    public record Kpis(int totalJobs, int openJobs, int applications, double averageMatch, double eligibleRate,
                       int shortlisted, int interviewed, int hired, double averageDaysToHire,
                       double averageDaysToFirstResponse, double averageRating, int reviewCount) { }

    public record FunnelStage(String key, String label, long count, double percentOfApplied) { }

    public record SkillStat(String skill, long applicants, double averageLevel, long verified) { }

    public record GapStat(String skill, long missing, double percentOfApplicants) { }

    public record JobRow(String jobId, String title, String status, long applications, long shortlisted, long interviewed,
                         long hired, double averageMatch, double hireRate) { }

    public record Analytics(String jobId, Kpis kpis, List<FunnelStage> funnel, List<CountItem> applicationsPerWeek,
                            List<CountItem> statusBreakdown, List<CountItem> matchDistribution,
                            List<SkillStat> topApplicantSkills, List<GapStat> commonGaps,
                            List<CountItem> departments, List<JobRow> jobs) { }

    public Analytics analytics(String employerId, String jobId, int weeks) {
        List<Job> allJobs = jobRepository.findByEmployerIdOrderByCreatedAtDesc(employerId);
        List<Job> jobs = allJobs;
        if (jobId != null && !jobId.isBlank()) {
            jobs = allJobs.stream().filter(j -> j.getId().equals(jobId)).toList();
            if (jobs.isEmpty()) {
                throw ApiException.notFound("Job not found among your jobs.");
            }
        }
        Set<String> jobIds = jobs.stream().map(Job::getId).collect(Collectors.toSet());
        List<JobApplication> apps = applicationRepository.findByEmployerId(employerId).stream()
                .filter(a -> jobIds.contains(a.getJobId())).toList();
        List<Interview> interviews = interviewRepository.findByEmployerId(employerId).stream()
                .filter(i -> jobIds.contains(i.getJobId()) && i.getStatus() != InterviewStatus.CANCELLED).toList();
        Set<String> interviewedApps = interviews.stream().map(Interview::getApplicationId).collect(Collectors.toSet());
        Map<String, StudentProfile> profiles = profileRepository.findByUserIdIn(
                        apps.stream().map(JobApplication::getStudentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(StudentProfile::getUserId, Function.identity(), (a, b) -> a));

        int n = apps.size();
        long reviewed = apps.stream().filter(a -> a.getStatus() != ApplicationStatus.APPLIED).count();
        long shortlisted = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED || a.getStatus() == ApplicationStatus.HIRED).count();
        long interviewed = apps.stream().filter(a -> interviewedApps.contains(a.getId())).count();
        long hired = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.HIRED).count();

        List<FunnelStage> funnel = List.of(
                new FunnelStage("applied", "Applied", n, n == 0 ? 0 : 100),
                new FunnelStage("reviewed", "Reviewed", reviewed, pct(reviewed, n)),
                new FunnelStage("shortlisted", "Shortlisted", shortlisted, pct(shortlisted, n)),
                new FunnelStage("interviewed", "Interviewed", interviewed, pct(interviewed, n)),
                new FunnelStage("hired", "Hired", hired, pct(hired, n)));

        double avgMatch = round1(apps.stream().mapToDouble(JobApplication::getMatchPercent).average().orElse(0));
        double eligibleRate = pct(apps.stream().filter(JobApplication::isEligible).count(), n);
        double daysToHire = round1(apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.HIRED && a.getAppliedAt() != null && a.getUpdatedAt() != null)
                .mapToLong(a -> ChronoUnit.DAYS.between(a.getAppliedAt(), a.getUpdatedAt())).average().orElse(0));
        double daysToResponse = round1(apps.stream()
                .filter(a -> a.getStatus() != ApplicationStatus.APPLIED && a.getAppliedAt() != null && a.getUpdatedAt() != null)
                .mapToDouble(a -> ChronoUnit.HOURS.between(a.getAppliedAt(), a.getUpdatedAt()) / 24.0).average().orElse(0));
        CompanyReviewService.RatingSummary rating = reviewService.summary(employerId);

        Kpis kpis = new Kpis(jobs.size(), (int) jobs.stream().filter(j -> j.getStatus() == JobStatus.OPEN).count(), n,
                avgMatch, eligibleRate, (int) shortlisted, (int) interviewed, (int) hired, daysToHire, daysToResponse,
                rating.average(), (int) rating.count());

        return new Analytics(jobId, kpis, funnel, perWeek(apps, Math.max(4, Math.min(26, weeks))), statusBreakdown(apps),
                matchDistribution(apps), topSkills(apps, profiles), commonGaps(apps, jobs, profiles),
                departments(apps), jobRows(jobs, apps, interviewedApps));
    }

    private static List<CountItem> perWeek(List<JobApplication> apps, int weeks) {
        LocalDate thisWeek = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, Long> buckets = new LinkedHashMap<>();
        for (int i = weeks - 1; i >= 0; i--) {
            buckets.put(thisWeek.minusWeeks(i), 0L);
        }
        for (JobApplication a : apps) {
            if (a.getAppliedAt() == null) {
                continue;
            }
            LocalDate wk = a.getAppliedAt().atZone(ZONE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            buckets.computeIfPresent(wk, (k, v) -> v + 1);
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM");
        List<CountItem> list = new ArrayList<>();
        buckets.forEach((k, v) -> list.add(new CountItem(fmt.format(k), v)));
        return list;
    }

    private static List<CountItem> statusBreakdown(List<JobApplication> apps) {
        List<CountItem> list = new ArrayList<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            list.add(new CountItem(s.name(), apps.stream().filter(a -> a.getStatus() == s).count()));
        }
        return list;
    }

    static List<CountItem> matchDistribution(List<JobApplication> apps) {
        String[] labels = {"0-19%", "20-39%", "40-59%", "60-79%", "80-100%"};
        long[] counts = new long[5];
        for (JobApplication a : apps) {
            int bucket = (int) Math.min(4, Math.max(0, Math.floor(a.getMatchPercent() / 20.0)));
            counts[bucket]++;
        }
        List<CountItem> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(new CountItem(labels[i], counts[i]));
        }
        return list;
    }

    private static List<SkillStat> topSkills(List<JobApplication> apps, Map<String, StudentProfile> profiles) {
        Map<String, List<StudentSkill>> bySkill = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (JobApplication a : apps) {
            if (!seen.add(a.getStudentId())) {
                continue; // count each applicant once even if they applied to several jobs
            }
            StudentProfile p = profiles.get(a.getStudentId());
            if (p == null) {
                continue;
            }
            for (StudentSkill s : p.getSkills()) {
                bySkill.computeIfAbsent(s.getSkillName(), k -> new ArrayList<>()).add(s);
            }
        }
        return bySkill.entrySet().stream()
                .map(e -> new SkillStat(e.getKey(), e.getValue().size(),
                        round1(e.getValue().stream().mapToInt(StudentSkill::getLevel).average().orElse(0)),
                        e.getValue().stream().filter(StudentSkill::isVerified).count()))
                .sorted(Comparator.comparingLong(SkillStat::applicants).reversed().thenComparing(SkillStat::skill))
                .limit(10).toList();
    }

    /** For each skill your jobs require: how many of the applicants to those jobs lack it entirely. */
    private static List<GapStat> commonGaps(List<JobApplication> apps, List<Job> jobs, Map<String, StudentProfile> profiles) {
        Map<String, Job> jobById = jobs.stream().collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<String, Long> missing = new HashMap<>();
        Map<String, Long> considered = new HashMap<>();
        for (JobApplication a : apps) {
            Job job = jobById.get(a.getJobId());
            StudentProfile p = profiles.get(a.getStudentId());
            if (job == null || p == null) {
                continue;
            }
            Set<String> has = p.getSkills().stream().map(StudentSkill::getSkillId).collect(Collectors.toSet());
            for (RequiredSkill rs : job.getRequiredSkills()) {
                considered.merge(rs.getSkillName(), 1L, Long::sum);
                if (!has.contains(rs.getSkillId())) {
                    missing.merge(rs.getSkillName(), 1L, Long::sum);
                }
            }
        }
        return missing.entrySet().stream()
                .map(e -> new GapStat(e.getKey(), e.getValue(), pct(e.getValue(), considered.getOrDefault(e.getKey(), 1L))))
                .sorted(Comparator.comparingLong(GapStat::missing).reversed().thenComparing(GapStat::skill))
                .limit(8).toList();
    }

    private static List<CountItem> departments(List<JobApplication> apps) {
        Map<String, Long> counts = apps.stream()
                .collect(Collectors.groupingBy(a -> a.getDepartment() == null || a.getDepartment().isBlank()
                        ? "Not given" : a.getDepartment().trim().toUpperCase(), Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(6).map(e -> new CountItem(e.getKey(), e.getValue())).toList();
    }

    private static List<JobRow> jobRows(List<Job> jobs, List<JobApplication> apps, Set<String> interviewedApps) {
        Map<String, List<JobApplication>> byJob = apps.stream().collect(Collectors.groupingBy(JobApplication::getJobId));
        List<JobRow> rows = new ArrayList<>();
        for (Job j : jobs) {
            List<JobApplication> list = byJob.getOrDefault(j.getId(), List.of());
            long sl = list.stream().filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED || a.getStatus() == ApplicationStatus.HIRED).count();
            long iv = list.stream().filter(a -> interviewedApps.contains(a.getId())).count();
            long h = list.stream().filter(a -> a.getStatus() == ApplicationStatus.HIRED).count();
            rows.add(new JobRow(j.getId(), j.getTitle(), j.getStatus() == null ? null : j.getStatus().name(), list.size(),
                    sl, iv, h, round1(list.stream().mapToDouble(JobApplication::getMatchPercent).average().orElse(0)),
                    pct(h, list.size())));
        }
        rows.sort(Comparator.comparingLong(JobRow::applications).reversed());
        return rows;
    }

    static double pct(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
