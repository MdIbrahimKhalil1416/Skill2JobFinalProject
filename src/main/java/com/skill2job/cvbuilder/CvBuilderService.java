package com.skill2job.cvbuilder;

import com.skill2job.entity.CourseProgress;
import com.skill2job.entity.CvCertification;
import com.skill2job.entity.CvEducation;
import com.skill2job.entity.CvExperience;
import com.skill2job.entity.CvProject;
import com.skill2job.entity.StudentProfile;
import com.skill2job.entity.StudentSkill;
import com.skill2job.enums.CourseProgressStatus;
import com.skill2job.gamification.GamificationService;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.UserRepository;
import com.skill2job.service.StudentService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CV builder: stores the CV-only sections (projects, experience, education, ...) on the student
 * profile and merges them with what the profile already knows (skills with verified levels,
 * completed courses, links). The PDF itself is rendered in the browser from this data.
 */
@Service
public class CvBuilderService {

    private final StudentService studentService;
    private final StudentProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    public CvBuilderService(StudentService studentService, StudentProfileRepository profileRepository,
                            UserRepository userRepository, GamificationService gamificationService) {
        this.studentService = studentService;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.gamificationService = gamificationService;
    }

    public CvBuilderDtos.CvResponse get(String studentId) {
        return toResponse(studentService.getOrCreate(studentId));
    }

    public CvBuilderDtos.CvResponse save(String studentId, CvBuilderDtos.CvRequest req) {
        StudentProfile p = studentService.getOrCreate(studentId);
        p.setHeadline(trim(req.headline()));
        p.setPhone(trim(req.phone()));
        p.setCity(trim(req.city()));
        p.setProjects(new ArrayList<>(nn(req.projects()).stream().filter(java.util.Objects::nonNull).map(x -> {
            CvProject e = new CvProject();
            e.setTitle(trim(x.title()));
            e.setTechStack(trim(x.techStack()));
            e.setDescription(trim(x.description()));
            e.setLink(trim(x.link()));
            return e;
        }).toList()));
        p.setExperiences(new ArrayList<>(nn(req.experiences()).stream().filter(java.util.Objects::nonNull).map(x -> {
            CvExperience e = new CvExperience();
            e.setTitle(trim(x.title()));
            e.setOrganization(trim(x.organization()));
            e.setStartDate(trim(x.startDate()));
            e.setEndDate(trim(x.endDate()));
            e.setDescription(trim(x.description()));
            return e;
        }).toList()));
        p.setEducation(new ArrayList<>(nn(req.education()).stream().filter(java.util.Objects::nonNull).map(x -> {
            CvEducation e = new CvEducation();
            e.setDegree(trim(x.degree()));
            e.setInstitution(trim(x.institution()));
            e.setStartYear(trim(x.startYear()));
            e.setEndYear(trim(x.endYear()));
            e.setResult(trim(x.result()));
            return e;
        }).toList()));
        p.setCertifications(new ArrayList<>(nn(req.certifications()).stream().filter(java.util.Objects::nonNull).map(x -> {
            CvCertification e = new CvCertification();
            e.setName(trim(x.name()));
            e.setIssuer(trim(x.issuer()));
            e.setYear(trim(x.year()));
            e.setLink(trim(x.link()));
            return e;
        }).toList()));
        p.setLanguages(new ArrayList<>(nn(req.languages()).stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(x -> !x.isEmpty()).distinct().toList()));
        p.setCvTemplate(req.template() == null || req.template().isBlank() ? "modern" : req.template());
        StudentProfile saved = profileRepository.save(p);
        if (!saved.getProjects().isEmpty() || !saved.getEducation().isEmpty()) {
            gamificationService.award(studentId, 15, "Built your CV", "CV_BUILT");
        }
        return toResponse(saved);
    }

    private CvBuilderDtos.CvResponse toResponse(StudentProfile p) {
        String email = userRepository.findById(p.getUserId()).map(u -> u.getEmail()).orElse(null);
        List<CvBuilderDtos.SkillLine> skills = p.getSkills().stream()
                .sorted(Comparator.comparingInt(StudentSkill::getLevel).reversed()
                        .thenComparing(StudentSkill::getSkillName, String.CASE_INSENSITIVE_ORDER))
                .map(s -> new CvBuilderDtos.SkillLine(s.getSkillName(), s.getLevel(), s.isVerified(), s.getVerifiedLevel(), s.isEndorsed()))
                .toList();
        List<String> courses = p.getCourseProgress().stream()
                .filter(c -> c.getStatus() == CourseProgressStatus.COMPLETED)
                .map(CourseProgress::getCourseTitle).toList();
        return new CvBuilderDtos.CvResponse(p.getFullName(), email, p.getHeadline(), p.getPhone(), p.getCity(),
                p.getDepartment(), p.getCgpa(), p.getGraduationYear(), p.getBio(),
                p.getGithubUrl(), p.getLinkedinUrl(), p.getWebsiteUrl(), skills, courses,
                p.getProjects().stream().map(x -> new CvBuilderDtos.Project(x.getTitle(), x.getTechStack(), x.getDescription(), x.getLink())).toList(),
                p.getExperiences().stream().map(x -> new CvBuilderDtos.Experience(x.getTitle(), x.getOrganization(), x.getStartDate(), x.getEndDate(), x.getDescription())).toList(),
                p.getEducation().stream().map(x -> new CvBuilderDtos.Education(x.getDegree(), x.getInstitution(), x.getStartYear(), x.getEndYear(), x.getResult())).toList(),
                p.getCertifications().stream().map(x -> new CvBuilderDtos.Certification(x.getName(), x.getIssuer(), x.getYear(), x.getLink())).toList(),
                p.getLanguages().stream().filter(java.util.Objects::nonNull).toList(), p.getCvTemplate() == null ? "modern" : p.getCvTemplate(), p.hasCv());
    }

    private static <T> List<T> nn(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
