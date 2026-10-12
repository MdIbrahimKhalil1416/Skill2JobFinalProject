package com.skill2job.service;

import com.skill2job.gamification.GamificationService;
import com.skill2job.audit.Audited;
import com.skill2job.dto.CourseProgressRequest;
import com.skill2job.dto.StudentProfileRequest;
import com.skill2job.dto.StudentProfileResponse;
import com.skill2job.dto.StudentSkillRequest;
import com.skill2job.entity.Course;
import com.skill2job.entity.CourseProgress;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.enums.CourseProgressStatus;
import com.skill2job.enums.Role;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.CourseRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class StudentService {

    public static final int MAX_SKILLS = 40;

    private final StudentProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final SkillService skillService;
    private final CourseRepository courseRepository;
    private final GamificationService gamificationService;

    private final NotificationService notificationService;

    public StudentService(StudentProfileRepository profileRepository, UserRepository userRepository,
                          SkillService skillService, CourseRepository courseRepository,
                          GamificationService gamificationService, NotificationService notificationService) {
        this.notificationService = notificationService;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.skillService = skillService;
        this.courseRepository = courseRepository;
        this.gamificationService = gamificationService;
    }

    /** Returns the profile, creating an empty one if it is somehow missing. */
    public StudentProfile getOrCreate(String userId) {
        return profileRepository.findByUserId(userId).orElseGet(() -> {
            StudentProfile p = new StudentProfile();
            p.setUserId(userId);
            userRepository.findById(userId).ifPresent(u -> p.setFullName(u.getName()));
            return profileRepository.save(p);
        });
    }

    /** Read-only lookup: unlike {@link #getOrCreate} it never creates a profile (a GET must not write). */
    public java.util.Optional<StudentProfile> findProfile(String userId) {
        return profileRepository.findByUserId(userId);
    }

    /** Drops the CV reference only if it still points at {@code stored} (the file turned out to be gone) -
     * a CV the student uploaded in the meantime is left alone. */
    public void clearCvIfStored(String userId, String stored) {
        profileRepository.findByUserId(userId).ifPresent(p -> {
            if (stored != null && stored.equals(p.getCvStoredFilename())) {
                p.setCvStoredFilename(null);
                p.setCvOriginalFilename(null);
                p.setCvUploadedAt(null);
                profileRepository.save(p);
            }
        });
    }

    public StudentProfileResponse getProfile(String userId) {
        return StudentProfileResponse.from(getOrCreate(userId));
    }

    /** Pairs the updated profile with whatever stored CV filename it just replaced (null if none), so the caller (CvService) can delete the old file from disk. */
    public record CvSwapResult(StudentProfileResponse profile, String previousStoredFilename) {
    }

    public CvSwapResult setCv(String userId, String storedFilename, String originalFilename) {
        StudentProfile p = getOrCreate(userId);
        String previous = p.getCvStoredFilename();
        p.setCvStoredFilename(storedFilename);
        p.setCvOriginalFilename(originalFilename);
        p.setCvUploadedAt(Instant.now());
        return new CvSwapResult(StudentProfileResponse.from(profileRepository.save(p)), previous);
    }

    /** Clears the CV reference and returns the stored filename that was removed, for the caller to delete the file on disk. */
    public String clearCv(String userId) {
        StudentProfile p = getOrCreate(userId);
        if (!p.hasCv()) {
            throw ApiException.notFound("You have not uploaded a CV.");
        }
        String stored = p.getCvStoredFilename();
        p.setCvStoredFilename(null);
        p.setCvOriginalFilename(null);
        p.setCvUploadedAt(null);
        profileRepository.save(p);
        return stored;
    }

    public StudentProfileResponse updateProfile(String userId, StudentProfileRequest req) {
        StudentProfile p = getOrCreate(userId);
        p.setFullName(trimOrNull(req.fullName()));
        p.setDepartment(trimOrNull(req.department()));
        p.setCgpa(req.cgpa());
        p.setGraduationYear(req.graduationYear());
        p.setBio(trimOrNull(req.bio()));
        p.setGithubUrl(trimOrNull(req.githubUrl()));
        p.setLinkedinUrl(trimOrNull(req.linkedinUrl()));
        p.setWebsiteUrl(trimOrNull(req.websiteUrl()));
        return StudentProfileResponse.from(profileRepository.save(p));
    }

    /** Adds a skill, or changes its level if the student already has it (no duplicates). */
    public StudentProfileResponse upsertSkill(String userId, StudentSkillRequest req) {
        Skill skill = skillService.getEntity(req.skillId());
        StudentProfile p = getOrCreate(userId);

        Optional<StudentSkill> existing = p.getSkills().stream()
                .filter(s -> s.getSkillId().equals(skill.getId()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setLevel(req.level());
            existing.get().setSkillName(skill.getName());
        } else {
            if (p.getSkills().size() >= MAX_SKILLS) {
                throw ApiException.badRequest("You can add at most " + MAX_SKILLS + " skills.");
            }
            p.getSkills().add(new StudentSkill(skill.getId(), skill.getName(), req.level()));
        }
        p.getSkills().sort(Comparator.comparing(StudentSkill::getSkillName, String.CASE_INSENSITIVE_ORDER));
        StudentProfileResponse response = StudentProfileResponse.from(profileRepository.save(p));
        if (existing.isEmpty()) {
            gamificationService.award(userId, 5, "Added " + skill.getName() + " to your skills", "SKILL:" + skill.getId());
        }
        return response;
    }

    public StudentProfileResponse removeSkill(String userId, String skillId) {
        StudentProfile p = getOrCreate(userId);
        boolean removed = p.getSkills().removeIf(s -> s.getSkillId().equals(skillId));
        if (!removed) {
            throw ApiException.notFound("This skill is not in your profile.");
        }
        return StudentProfileResponse.from(profileRepository.save(p));
    }

    /** Marks a suggested course "In Progress" or "Completed" (or changes its status). */
    public StudentProfileResponse upsertCourseProgress(String userId, CourseProgressRequest req) {
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> ApiException.notFound("Course not found."));
        StudentProfile p = getOrCreate(userId);
        Optional<CourseProgress> existing = p.getCourseProgress().stream()
                .filter(c -> c.getCourseId().equals(course.getId()))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setStatus(req.status());
            existing.get().setUpdatedAt(Instant.now());
        } else {
            p.getCourseProgress().add(new CourseProgress(course.getId(), course.getTitle(), req.status(), Instant.now()));
        }
        StudentProfileResponse response = StudentProfileResponse.from(profileRepository.save(p));
        if (req.status() == CourseProgressStatus.COMPLETED) {
            gamificationService.award(userId, 15, "Completed \"" + course.getTitle() + "\"", "COURSE_DONE:" + course.getId());
        } else {
            gamificationService.award(userId, 5, "Started \"" + course.getTitle() + "\"", "COURSE_START:" + course.getId());
        }
        return response;
    }

    public StudentProfileResponse removeCourseProgress(String userId, String courseId) {
        StudentProfile p = getOrCreate(userId);
        boolean removed = p.getCourseProgress().removeIf(c -> c.getCourseId().equals(courseId));
        if (!removed) {
            throw ApiException.notFound("This course is not tracked on your profile.");
        }
        return StudentProfileResponse.from(profileRepository.save(p));
    }

    /**
     * Admin or ANY other student can endorse a skill on this profile (like a LinkedIn skill
     * endorsement). A student can never endorse their own skill.
     */
    @Audited(action = "SKILL_ENDORSE", targetType = "STUDENT", targetArg = 1, detailArg = 2)
    public StudentProfileResponse setEndorsement(AuthUser requester, String studentUserId, String skillId, boolean endorsed) {
        if (requester.role() == Role.STUDENT && requester.id().equals(studentUserId)) {
            throw ApiException.badRequest("You cannot endorse your own skill.");
        }
        if (requester.role() != Role.ADMIN && requester.role() != Role.STUDENT) {
            throw ApiException.forbidden("Only a student or an admin can endorse a skill.");
        }
        StudentProfile p = getOrCreate(studentUserId);
        StudentSkill skill = p.getSkills().stream()
                .filter(s -> s.getSkillId().equals(skillId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("This student does not have that skill."));
        boolean newlyEndorsed = endorsed && !skill.isEndorsed();
        skill.setEndorsed(endorsed);
        skill.setEndorsedByName(endorsed ? requester.name() : null);
        skill.setEndorsedAt(endorsed ? Instant.now() : null);
        StudentProfileResponse saved = StudentProfileResponse.from(profileRepository.save(p));
        if (newlyEndorsed && notificationService != null) {
            notificationService.notify(studentUserId, requester.name() + " endorsed your skill " + skill.getSkillName() + ".",
                    NotificationLinks.mySkills());
        }
        return saved;
    }

    /** skillId -> level, the input format of MatchCalculator. */
    public static Map<String, Integer> levelMap(StudentProfile p) {
        Map<String, Integer> map = new HashMap<>();
        for (StudentSkill s : p.getSkills()) {
            map.put(s.getSkillId(), s.getLevel());
        }
        return map;
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
