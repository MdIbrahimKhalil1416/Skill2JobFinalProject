package com.skill2job.roadmap;

import com.skill2job.entity.Course;
import com.skill2job.entity.CourseProgress;
import com.skill2job.entity.Job;
import com.skill2job.entity.QuizQuestion;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.CourseProgressStatus;
import com.skill2job.enums.JobStatus;
import com.skill2job.matching.MatchCalculator;
import com.skill2job.matching.MatchResult;
import com.skill2job.matching.SkillGap;
import com.skill2job.quiz.QuizService;
import com.skill2job.repository.CourseRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.QuizQuestionRepository;
import com.skill2job.service.JobService;
import com.skill2job.service.StudentService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds a week-by-week learning roadmap from a student's skill gaps - either for one target job,
 * or (no job chosen) for the skills most demanded by all open jobs. Reuses MatchCalculator for the
 * gaps and the course catalog for the steps; the planning itself is RoadmapPlanner.
 */
@Service
public class RoadmapService {

    /** "Market" roadmap: how many of the most-demanded missing skills to include. */
    public static final int MARKET_SKILLS = 5;

    private final StudentService studentService;
    private final JobService jobService;
    private final JobRepository jobRepository;
    private final CourseRepository courseRepository;
    private final QuizQuestionRepository quizQuestionRepository;

    public RoadmapService(StudentService studentService, JobService jobService, JobRepository jobRepository,
                          CourseRepository courseRepository, QuizQuestionRepository quizQuestionRepository) {
        this.studentService = studentService;
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.courseRepository = courseRepository;
        this.quizQuestionRepository = quizQuestionRepository;
    }

    public record RoadmapResponse(String target, String jobId, String jobTitle, String companyName, double currentMatch,
                                  double projectedMatch, int hoursPerWeek, int totalHours, int totalWeeks,
                                  LocalDate estimatedFinish, List<RoadmapPlanner.Week> weeks,
                                  List<RoadmapPlanner.Step> alreadyDone, List<SkillTarget> skills,
                                  List<String> noCourseSkills) { }

    public record SkillTarget(String skillId, String skillName, int fromLevel, int toLevel, boolean mandatory) { }

    public RoadmapResponse build(String studentId, String jobId, int hoursPerWeek) {
        StudentProfile profile = studentService.getOrCreate(studentId);
        Map<String, Integer> levels = StudentService.levelMap(profile);

        String target;
        Job job = null;
        List<RoadmapPlanner.Gap> gaps;
        double current;
        double projected;
        if (jobId != null && !jobId.isBlank()) {
            job = jobService.getVisibleEntity(jobId, null);   // students never see unpaid, unpublished jobs
            target = "JOB";
            MatchResult r = MatchCalculator.calculate(levels, job.getRequiredSkills());
            List<SkillGap> all = new ArrayList<>(r.missing());
            all.addAll(r.weak());
            gaps = all.stream().map(g -> new RoadmapPlanner.Gap(g.skillId(), g.skillName(), g.studentLevel(),
                    g.requiredLevel(), g.mandatory(), g.priority())).toList();
            current = r.matchPercent();
            Map<String, Integer> after = new HashMap<>(levels);
            gaps.forEach(g -> after.merge(g.skillId(), g.requiredLevel(), Math::max));
            projected = MatchCalculator.calculate(after, job.getRequiredSkills()).matchPercent();
        } else {
            target = "MARKET";
            gaps = marketGaps(levels);
            current = averageMatch(levels);
            Map<String, Integer> after = new HashMap<>(levels);
            gaps.forEach(g -> after.merge(g.skillId(), g.requiredLevel(), Math::max));
            projected = averageMatch(after);
        }

        Set<String> skillIds = gaps.stream().map(RoadmapPlanner.Gap::skillId).collect(Collectors.toSet());
        Map<String, List<RoadmapPlanner.CourseInfo>> courses = new HashMap<>();
        for (Course c : courseRepository.findAll()) {
            for (String sid : c.getSkillIds()) {
                if (skillIds.contains(sid)) {
                    courses.computeIfAbsent(sid, k -> new ArrayList<>()).add(new RoadmapPlanner.CourseInfo(
                            c.getId(), c.getTitle(), c.getPlatform(), c.getUrl(), c.getLevel(), c.isFree()));
                }
            }
        }
        Map<String, RoadmapPlanner.Status> progress = new HashMap<>();
        for (CourseProgress cp : profile.getCourseProgress()) {
            progress.put(cp.getCourseId(), cp.getStatus() == CourseProgressStatus.COMPLETED
                    ? RoadmapPlanner.Status.DONE : RoadmapPlanner.Status.IN_PROGRESS);
        }
        Set<String> withTest = skillsWithTest();

        RoadmapPlanner.Plan plan = RoadmapPlanner.plan(gaps, courses, progress, withTest, hoursPerWeek);
        int perWeek = Math.max(2, Math.min(40, hoursPerWeek));
        List<SkillTarget> targets = gaps.stream()
                .map(g -> new SkillTarget(g.skillId(), g.skillName(), g.studentLevel(), g.requiredLevel(), g.mandatory()))
                .toList();
        return new RoadmapResponse(target, job == null ? null : job.getId(), job == null ? null : job.getTitle(),
                job == null ? null : job.getCompanyName(), current, projected, perWeek, plan.totalHours(),
                plan.weeks().size(), JobService.today().plusWeeks(plan.weeks().size()), plan.weeks(), plan.alreadyDone(),
                targets, plan.noCourseSkills());
    }

    /** Skills (with at least MIN_QUESTIONS questions) that have a skill test. */
    private Set<String> skillsWithTest() {
        Map<String, Long> counts = quizQuestionRepository.findAll().stream()
                .collect(Collectors.groupingBy(QuizQuestion::getSkillId, Collectors.counting()));
        Set<String> set = new HashSet<>();
        counts.forEach((k, v) -> {
            if (v >= QuizService.MIN_QUESTIONS) {
                set.add(k);
            }
        });
        return set;
    }

    /**
     * The skills open jobs ask for most that this student lacks or is weak in. Target level = the
     * level most of those jobs require (rounded average), priority = how many jobs need the skill.
     */
    List<RoadmapPlanner.Gap> marketGaps(Map<String, Integer> levels) {
        Map<String, List<RequiredSkill>> bySkill = new HashMap<>();
        for (Job j : jobRepository.findByStatus(JobStatus.OPEN)) {
            for (RequiredSkill rs : j.getRequiredSkills()) {
                bySkill.computeIfAbsent(rs.getSkillId(), k -> new ArrayList<>()).add(rs);
            }
        }
        List<RoadmapPlanner.Gap> gaps = new ArrayList<>();
        bySkill.forEach((skillId, list) -> {
            int required = (int) Math.round(list.stream().mapToInt(RequiredSkill::getRequiredLevel).average().orElse(3));
            int have = levels.getOrDefault(skillId, 0);
            if (have < required) {
                boolean mostlyMandatory = list.stream().filter(RequiredSkill::isMandatory).count() * 2 > list.size();
                gaps.add(new RoadmapPlanner.Gap(skillId, list.get(0).getSkillName(), have, required, mostlyMandatory, list.size()));
            }
        });
        gaps.sort(Comparator.comparingInt(RoadmapPlanner.Gap::priority).reversed()
                .thenComparing(RoadmapPlanner.Gap::skillName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return gaps.stream().limit(MARKET_SKILLS).toList();
    }

    private double averageMatch(Map<String, Integer> levels) {
        List<Job> open = jobRepository.findByStatus(JobStatus.OPEN);
        if (open.isEmpty()) {
            return 0;
        }
        double avg = open.stream().mapToDouble(j -> MatchCalculator.calculate(levels, j.getRequiredSkills()).matchPercent())
                .average().orElse(0);
        return Math.round(avg * 10.0) / 10.0;
    }
}
