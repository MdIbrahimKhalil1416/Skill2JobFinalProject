package com.skill2job.service;

import com.skill2job.audit.Audited;
import com.skill2job.dto.CourseRequest;
import com.skill2job.dto.CourseResponse;
import com.skill2job.dto.CourseSuggestion;
import com.skill2job.dto.PageResponse;
import com.skill2job.entity.Course;
import com.skill2job.entity.Skill;
import com.skill2job.exception.ApiException;
import com.skill2job.matching.SkillGap;
import com.skill2job.repository.CourseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class CourseService {

    public static final int COURSES_PER_SKILL = 3;

    private final CourseRepository courseRepository;
    private final SkillService skillService;
    private final MongoTemplate mongoTemplate;

    public CourseService(CourseRepository courseRepository, SkillService skillService, MongoTemplate mongoTemplate) {
        this.courseRepository = courseRepository;
        this.skillService = skillService;
        this.mongoTemplate = mongoTemplate;
    }

    public PageResponse<CourseResponse> list(String skillId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Query query = new Query();
        if (skillId != null && !skillId.isBlank()) {
            query.addCriteria(Criteria.where("skillIds").is(skillId));
        }
        long total = mongoTemplate.count(query, Course.class);
        query.with(Sort.by("title")).skip((long) safePage * safeSize).limit(safeSize);
        List<Course> courses = mongoTemplate.find(query, Course.class);
        return PageResponse.of(toResponses(courses), safePage, safeSize, total);
    }

    @Audited(action = "COURSE_CREATE", targetType = "COURSE", detailArg = 0)
    public CourseResponse create(CourseRequest req) {
        Course c = new Course();
        apply(c, req);
        return toResponses(List.of(courseRepository.save(c))).get(0);
    }

    @Audited(action = "COURSE_UPDATE", targetType = "COURSE", targetArg = 0)
    public CourseResponse update(String id, CourseRequest req) {
        Course c = courseRepository.findById(id).orElseThrow(() -> ApiException.notFound("Course not found."));
        apply(c, req);
        return toResponses(List.of(courseRepository.save(c))).get(0);
    }

    @Audited(action = "COURSE_DELETE", targetType = "COURSE", targetArg = 0)
    public void delete(String id) {
        if (!courseRepository.existsById(id)) {
            throw ApiException.notFound("Course not found.");
        }
        courseRepository.deleteById(id);
    }

    /**
     * Course suggestions for the student's missing and weak skills.
     * Skills are ordered by priority (weight x gap). For each skill:
     * free courses first, then the course whose level is closest to the student's next level.
     * All courses are loaded in ONE query (no query per skill).
     */
    public List<CourseSuggestion> suggest(List<SkillGap> gaps) {
        if (gaps == null || gaps.isEmpty()) {
            return List.of();
        }
        List<SkillGap> ordered = new ArrayList<>(gaps);
        ordered.sort(Comparator.comparingInt(SkillGap::priority).reversed());

        Set<String> skillIds = new HashSet<>();
        ordered.forEach(g -> skillIds.add(g.skillId()));
        List<Course> candidates = mongoTemplate.find(
                new Query(Criteria.where("skillIds").in(skillIds)), Course.class);
        Map<String, Skill> skillMap = skillService.getMapByIds(collectSkillIds(candidates));

        List<CourseSuggestion> result = new ArrayList<>();
        for (SkillGap gap : ordered) {
            int target = Math.min(gap.studentLevel() + 1, 5);
            List<CourseResponse> courses = candidates.stream()
                    .filter(c -> c.getSkillIds().contains(gap.skillId()))
                    .sorted(Comparator.comparing((Course c) -> !c.isFree())
                            .thenComparingInt(c -> Math.abs(c.getLevel() - target))
                            .thenComparing(Course::getTitle, Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(COURSES_PER_SKILL)
                    .map(c -> toResponse(c, skillMap))
                    .toList();
            result.add(new CourseSuggestion(gap.skillId(), gap.skillName(), gap.gap(), gap.priority(), courses));
        }
        return result;
    }

    private List<CourseResponse> toResponses(List<Course> courses) {
        Map<String, Skill> skillMap = skillService.getMapByIds(collectSkillIds(courses));
        return courses.stream().map(c -> toResponse(c, skillMap)).toList();
    }

    private static Set<String> collectSkillIds(Collection<Course> courses) {
        Set<String> ids = new LinkedHashSet<>();
        courses.forEach(c -> ids.addAll(c.getSkillIds()));
        return ids;
    }

    private static CourseResponse toResponse(Course c, Map<String, Skill> skillMap) {
        List<String> names = c.getSkillIds().stream()
                .map(skillMap::get)
                .filter(Objects::nonNull)
                .map(Skill::getName)
                .toList();
        return new CourseResponse(c.getId(), c.getTitle(), c.getPlatform(), c.getUrl(), c.getLevel(), c.isFree(),
                List.copyOf(c.getSkillIds()), names);
    }

    private void apply(Course c, CourseRequest req) {
        Set<String> ids = new LinkedHashSet<>(req.skillIds());
        Map<String, Skill> found = skillService.getMapByIds(ids);
        for (String id : ids) {
            if (!found.containsKey(id)) {
                throw ApiException.badRequest("Unknown skill id: " + id);
            }
        }
        c.setTitle(req.title().trim());
        c.setPlatform(req.platform().trim());
        c.setUrl(req.url().trim());
        c.setLevel(req.level());
        c.setFree(req.free());
        c.setSkillIds(new ArrayList<>(ids));
    }
}
