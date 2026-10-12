package com.skill2job.service;

import com.skill2job.dto.CourseSuggestion;
import com.skill2job.dto.JobAnalysisResponse;
import com.skill2job.dto.JobMatchResponse;
import com.skill2job.dto.JobResponse;
import com.skill2job.entity.Job;
import com.skill2job.entity.JobApplication;
import com.skill2job.entity.StudentProfile;
import com.skill2job.enums.JobType;
import com.skill2job.matching.MatchCalculator;
import com.skill2job.matching.MatchResult;
import com.skill2job.matching.SkillGap;
import com.skill2job.repository.JobApplicationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Connects the database with the pure MatchCalculator. */
@Service
public class MatchingService {

    private final JobService jobService;
    private final StudentService studentService;
    private final CourseService courseService;
    private final JobApplicationRepository applicationRepository;

    public MatchingService(JobService jobService, StudentService studentService, CourseService courseService,
                           JobApplicationRepository applicationRepository) {
        this.jobService = jobService;
        this.studentService = studentService;
        this.courseService = courseService;
        this.applicationRepository = applicationRepository;
    }

    public MatchResult match(StudentProfile profile, Job job) {
        return MatchCalculator.calculate(StudentService.levelMap(profile), job.getRequiredSkills());
    }

    /** Match %, gap report and course suggestions for one job. */
    public JobAnalysisResponse analyze(String studentUserId, String jobId) {
        Job job = jobService.getVisibleEntity(jobId, null);
        StudentProfile profile = studentService.getOrCreate(studentUserId);
        MatchResult result = match(profile, job);

        List<SkillGap> toImprove = new ArrayList<>(result.missing());
        toImprove.addAll(result.weak());
        List<CourseSuggestion> suggestions = courseService.suggest(toImprove);

        Optional<JobApplication> application = applicationRepository.findByJobIdAndStudentId(jobId, studentUserId);
        return new JobAnalysisResponse(JobResponse.from(job), result, suggestions,
                application.isPresent(), application.map(a -> a.getStatus().name()).orElse(null));
    }

    /** All open jobs ranked by how well the student matches them (best first). */
    public List<JobMatchResponse> recommended(String studentUserId, String keyword, JobType type,
                                              String location, String skillId) {
        StudentProfile profile = studentService.getOrCreate(studentUserId);
        Map<String, Integer> levels = StudentService.levelMap(profile);
        return jobService.findOpenJobs(keyword, type, location, skillId).stream()
                .map(job -> {
                    MatchResult r = MatchCalculator.calculate(levels, job.getRequiredSkills());
                    return new JobMatchResponse(JobResponse.from(job), r.matchPercent(), r.eligible(), r.missing().size());
                })
                .sorted(Comparator.comparingDouble(JobMatchResponse::matchPercent).reversed()
                        .thenComparing((JobMatchResponse j) -> j.job().deadline(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
