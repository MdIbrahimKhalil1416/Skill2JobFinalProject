package com.skill2job.service;

import com.skill2job.dto.ApplicationResponse;
import com.skill2job.dto.CountItem;
import com.skill2job.dto.JobMatchResponse;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.ApplicationStatus;
import com.skill2job.enums.JobStatus;
import com.skill2job.enums.Role;
import com.skill2job.matching.MatchCalculator;
import com.skill2job.matching.MatchResult;
import com.skill2job.matching.SkillGap;
import com.skill2job.repository.CourseRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.SkillRepository;
import com.skill2job.repository.UserRepository;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Numbers and chart data for the three dashboards. */
@Service
public class DashboardService {

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final JobApplicationRepository applicationRepository;
    private final CourseRepository courseRepository;
    private final SkillRepository skillRepository;
    private final StudentService studentService;
    private final MatchingService matchingService;

    public DashboardService(MongoTemplate mongoTemplate, UserRepository userRepository, JobRepository jobRepository,
                            JobApplicationRepository applicationRepository, CourseRepository courseRepository,
                            SkillRepository skillRepository, StudentService studentService,
                            MatchingService matchingService) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.courseRepository = courseRepository;
        this.skillRepository = skillRepository;
        this.studentService = studentService;
        this.matchingService = matchingService;
    }

    // ---------------- ADMIN ----------------

    public Map<String, Object> admin() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("users", userRepository.count());
        totals.put("students", userRepository.countByRole(Role.STUDENT));
        totals.put("employers", userRepository.countByRole(Role.EMPLOYER));
        totals.put("jobs", jobRepository.count());
        totals.put("openJobs", jobRepository.countByStatus(JobStatus.OPEN));
        totals.put("applications", applicationRepository.count());
        totals.put("skills", skillRepository.count());
        totals.put("courses", courseRepository.count());
        data.put("totals", totals);
        data.put("topSkills", topDemandedSkills(10));
        data.put("usersByRole", usersByRole());
        data.put("jobsPerMonth", jobsPerMonth());
        return data;
    }

    /**
     * MongoDB aggregation pipeline:
     * match open jobs -> unwind requiredSkills -> group by skill name and count -> sort -> limit.
     */
    public List<CountItem> topDemandedSkills(int limit) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(JobStatus.OPEN.name())),
                Aggregation.unwind("requiredSkills"),
                Aggregation.group("requiredSkills.skillName").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(limit));
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "jobs", Document.class);
        return toCountItems(results.getMappedResults());
    }

    /** Aggregation: group users by role. */
    public List<CountItem> usersByRole() {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.group("role").count().as("count"));
        AggregationResults<Document> results = mongoTemplate.aggregate(agg, "users", Document.class);
        List<CountItem> items = new ArrayList<>(toCountItems(results.getMappedResults()));
        items.sort(Comparator.comparing(CountItem::label));
        return items;
    }

    /** Jobs created per month (yyyy-MM), oldest first. */
    public List<CountItem> jobsPerMonth() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
        Map<String, Long> months = new TreeMap<>();
        for (Job j : jobRepository.findAll()) {
            if (j.getCreatedAt() != null) {
                months.merge(fmt.format(j.getCreatedAt()), 1L, Long::sum);
            }
        }
        List<CountItem> list = new ArrayList<>();
        months.forEach((k, v) -> list.add(new CountItem(k, v)));
        return list;
    }

    private static List<CountItem> toCountItems(List<Document> docs) {
        List<CountItem> list = new ArrayList<>();
        for (Document d : docs) {
            Object id = d.get("_id");
            Object count = d.get("count");
            long n = count instanceof Number num ? num.longValue() : 0L;
            list.add(new CountItem(id == null ? "Unknown" : id.toString(), n));
        }
        return list;
    }

    // ---------------- STUDENT ----------------

    public Map<String, Object> student(String userId) {
        StudentProfile profile = studentService.getOrCreate(userId);
        List<JobMatchResponse> ranked = matchingService.recommended(userId, null, null, null, null);
        Map<String, Integer> levels = StudentService.levelMap(profile);

        double avg = ranked.stream().mapToDouble(JobMatchResponse::matchPercent).average().orElse(0);
        long eligibleCount = ranked.stream().filter(JobMatchResponse::eligible).count();

        // How often each skill is missing or weak across all open jobs.
        Map<String, Long> missingCounts = new HashMap<>();
        for (Job job : jobRepository.findByStatus(JobStatus.OPEN)) {
            MatchResult r = MatchCalculator.calculate(levels, job.getRequiredSkills());
            for (SkillGap g : r.missing()) {
                missingCounts.merge(g.skillName(), 1L, Long::sum);
            }
        }
        List<CountItem> topMissing = missingCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(8)
                .map(e -> new CountItem(e.getKey(), e.getValue()))
                .toList();

        List<ApplicationResponse> apps = applicationRepository.findByStudentIdOrderByAppliedAtDesc(userId).stream()
                .map(ApplicationResponse::from).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skillCount", profile.getSkills().size());
        data.put("openJobs", ranked.size());
        data.put("eligibleJobs", eligibleCount);
        data.put("averageMatch", Math.round(avg * 10.0) / 10.0);
        data.put("applications", apps.size());
        data.put("applicationStatus", statusCounts(apps.stream().map(ApplicationResponse::status).toList()));
        data.put("topMissingSkills", topMissing);
        data.put("topMatches", ranked.stream().limit(5).toList());
        data.put("profileComplete", profile.getDepartment() != null && !profile.getSkills().isEmpty());
        return data;
    }

    // ---------------- EMPLOYER ----------------

    public Map<String, Object> employer(String userId) {
        List<Job> jobs = jobRepository.findByEmployerIdOrderByCreatedAtDesc(userId);
        List<JobApplication> apps = applicationRepository.findByEmployerId(userId);

        Map<String, Long> perJob = new HashMap<>();
        for (JobApplication a : apps) {
            perJob.merge(a.getJobId(), 1L, Long::sum);
        }
        List<CountItem> applicantsPerJob = jobs.stream()
                .map(j -> new CountItem(j.getTitle(), perJob.getOrDefault(j.getId(), 0L)))
                .sorted(Comparator.comparingLong(CountItem::count).reversed())
                .limit(10)
                .toList();

        double avgMatch = apps.stream().mapToDouble(JobApplication::getMatchPercent).average().orElse(0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobs", jobs.size());
        data.put("openJobs", jobs.stream().filter(j -> j.getStatus() == JobStatus.OPEN).count());
        data.put("applications", apps.size());
        data.put("averageApplicantMatch", Math.round(avgMatch * 10.0) / 10.0);
        data.put("applicantsPerJob", applicantsPerJob);
        data.put("applicationStatus", statusCounts(apps.stream().map(JobApplication::getStatus).toList()));
        data.put("hiringFunnel", hiringFunnel(apps));
        return data;
    }

    /**
     * Hiring funnel: Applied -> Shortlisted -> Hired conversion rates, plus the average
     * time-to-hire in days (appliedAt -> updatedAt for applications that reached HIRED).
     */
    private Map<String, Object> hiringFunnel(List<JobApplication> apps) {
        long applied = apps.size();
        long shortlistedOrBeyond = apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED || a.getStatus() == ApplicationStatus.HIRED)
                .count();
        long hired = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.HIRED).count();

        double avgDaysToHire = apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.HIRED && a.getAppliedAt() != null && a.getUpdatedAt() != null)
                .mapToLong(a -> java.time.temporal.ChronoUnit.DAYS.between(a.getAppliedAt(), a.getUpdatedAt()))
                .average().orElse(0);

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("applied", applied);
        funnel.put("shortlisted", shortlistedOrBeyond);
        funnel.put("hired", hired);
        funnel.put("appliedToShortlistedRate", ratePercent(shortlistedOrBeyond, applied));
        funnel.put("shortlistedToHiredRate", ratePercent(hired, shortlistedOrBeyond));
        funnel.put("overallConversionRate", ratePercent(hired, applied));
        funnel.put("averageDaysToHire", Math.round(avgDaysToHire * 10.0) / 10.0);
        return funnel;
    }

    private static double ratePercent(long part, long whole) {
        return whole == 0 ? 0 : Math.round((part * 1000.0) / whole) / 10.0;
    }

    private static List<CountItem> statusCounts(List<ApplicationStatus> statuses) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        statuses.forEach(s -> counts.merge(s, 1L, Long::sum));
        List<CountItem> list = new ArrayList<>();
        counts.forEach((k, v) -> list.add(new CountItem(k.name(), v)));
        return list;
    }
}
