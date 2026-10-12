package com.skill2job.service;

import com.skill2job.dto.JobRequest;
import com.skill2job.dto.RequiredSkillRequest;
import com.skill2job.entity.EmployerProfile;
import com.skill2job.entity.Job;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.enums.JobType;
import com.skill2job.enums.Role;
import com.skill2job.repository.ChatMessageRepository;
import com.skill2job.repository.InterviewRepository;
import com.skill2job.repository.JobApplicationRepository;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.SavedJobRepository;
import com.skill2job.security.AuthUser;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory wiring of JobService and its collaborators for service-level tests. No database, no network. */
final class JobFixture {

    static final int FREE_POSTS = 3;

    final Map<String, Object> jobStore = new LinkedHashMap<>();
    final Map<String, Object> appStore = new LinkedHashMap<>();
    final Map<String, Object> savedStore = new LinkedHashMap<>();
    final Map<String, Object> interviewStore = new LinkedHashMap<>();
    final Map<String, Object> chatStore = new LinkedHashMap<>();
    final JobRepository jobs = MapRepo.of(JobRepository.class, jobStore);
    final JobApplicationRepository apps = MapRepo.of(JobApplicationRepository.class, appStore);
    final SavedJobRepository saved = MapRepo.of(SavedJobRepository.class, savedStore);
    final InterviewRepository interviews = MapRepo.of(InterviewRepository.class, interviewStore);
    final ChatMessageRepository chats = MapRepo.of(ChatMessageRepository.class, chatStore);
    final Map<String, EmployerProfile> employerProfiles = new HashMap<>();
    final Map<String, Skill> skillCatalog = new HashMap<>();
    final JobService jobService;

    JobFixture() {
        for (String id : new String[]{"java", "sql", "react"}) {
            Skill s = new Skill();
            s.setId(id);
            s.setName(id.toUpperCase());
            skillCatalog.put(id, s);
        }
        SkillService skills = new SkillService(null, null, null, null) {
            @Override public Map<String, Skill> getMapByIds(Collection<String> ids) {
                Map<String, Skill> out = new HashMap<>();
                for (String id : ids) if (skillCatalog.containsKey(id)) out.put(id, skillCatalog.get(id));
                return out;
            }
        };
        EmployerService employers = new EmployerService(null, null, null, null) {
            @Override public EmployerProfile getOrCreate(String userId) {
                return employerProfiles.computeIfAbsent(userId, id -> {
                    EmployerProfile p = new EmployerProfile();
                    p.setUserId(id);
                    p.setCompanyName("Co " + id);
                    return p;
                });
            }
            @Override public void incrementJobsPosted(EmployerProfile p) { p.setJobsPostedCount(p.getJobsPostedCount() + 1); }
        };
        PaymentService payments = new PaymentService(null, null, null, null, null, null, null);
        OtpServiceTest.set(payments, "freeJobPosts", FREE_POSTS);
        jobService = new JobService(jobs, apps, skills, employers, null, saved, interviews, chats, payments);
    }

    static AuthUser employer(String id) { return new AuthUser(id, id + "@co.com", id, Role.EMPLOYER); }
    static AuthUser student(String id) { return new AuthUser(id, id + "@uni.edu", id, Role.STUDENT); }
    static AuthUser admin() { return new AuthUser("root", "root@x.com", "root", Role.ADMIN); }

    static JobRequest request(LocalDate deadline, RequiredSkillRequest... skills) {
        return new JobRequest("  Backend Intern ", " Build APIs ", JobType.INTERNSHIP, " Dhaka ", deadline,
                skills.length == 0 ? List.of(new RequiredSkillRequest("java", 3, 3, false)) : List.of(skills));
    }

    static LocalDate in(int days) { return JobService.today().plusDays(days); }

    /** Posts a job as the given employer and returns the stored entity. */
    Job post(String employerId) {
        String id = jobService.create(employer(employerId), request(in(10))).id();
        return jobs.findById(id).get();
    }

    static StudentProfile studentProfile(String userId, String... skillIdLevel) {
        StudentProfile p = new StudentProfile();
        p.setUserId(userId);
        p.setFullName("Student " + userId);
        for (String sl : skillIdLevel) {
            String[] parts = sl.split(":");
            p.getSkills().add(new StudentSkill(parts[0], parts[0].toUpperCase(), Integer.parseInt(parts[1])));
        }
        return p;
    }
}
