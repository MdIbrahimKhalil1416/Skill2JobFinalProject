package com.skill2job.service;

import com.skill2job.dto.CourseRequest;
import com.skill2job.dto.CourseSuggestion;
import com.skill2job.entity.Course;
import com.skill2job.entity.Skill;
import com.skill2job.exception.ApiException;
import com.skill2job.matching.SkillGap;
import com.skill2job.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Course catalogue management and the per-skill suggestions that feed the skill-gap page. */
class CourseServiceTest {

    final Map<String, Object> store = new LinkedHashMap<>();
    final CourseRepository repo = MapRepoAccess.of(CourseRepository.class, store);
    final Set<String> knownSkills = Set.of("java", "sql", "git");

    /** The Mongo query itself cannot run here; the template just hands back every stored course. The factory points
     * at a closed local port and is never used: find() is overridden, so no database is ever contacted. */
    final MongoTemplate template = new MongoTemplate(new SimpleMongoClientDatabaseFactory("mongodb://127.0.0.1:1/unit-test-unused")) {
        @Override @SuppressWarnings("unchecked")
        public <T> List<T> find(Query q, Class<T> c) { return (List<T>) new ArrayList<>(store.values()); }
    };
    final SkillService skills = new SkillService(null, null, null, null) {
        @Override public Map<String, Skill> getMapByIds(java.util.Collection<String> ids) {
            Map<String, Skill> m = new LinkedHashMap<>();
            for (String id : ids) if (knownSkills.contains(id)) { Skill s = new Skill(); s.setId(id); s.setName(id.toUpperCase()); m.put(id, s); }
            return m;
        }
    };
    final CourseService svc = new CourseService(repo, skills, template);

    private static CourseRequest req(String title, int level, boolean free, String... skillIds) {
        return new CourseRequest(title, "Udemy", "https://example.com/" + title, level, free, List.of(skillIds));
    }

    private static SkillGap gap(String id, int studentLevel, int priority) {
        return new SkillGap(id, id.toUpperCase(), 4, studentLevel, 3, false, 4 - studentLevel, priority);
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void createTrimsFieldsAndResolvesSkillNames() {
        var c = svc.create(new CourseRequest("  Java Basics ", " Udemy ", " https://x.com/j ", 2, true, List.of("java", "java", "sql")));
        assertEquals("Java Basics", c.title());
        assertEquals("Udemy", c.platform());
        assertEquals("https://x.com/j", c.url());
        assertEquals(List.of("java", "sql"), c.skillIds(), "a repeated skill is stored once");
        assertEquals(List.of("JAVA", "SQL"), c.skillNames());
    }

    @Test
    void createRejectsAnUnknownSkillAndStoresNothing() {
        assertEquals(400, status(() -> svc.create(req("Bad", 1, true, "java", "ghost"))));
        assertTrue(store.isEmpty());
    }

    @Test
    void updateChangesTheCourseAndUnknownIdIs404() {
        String id = svc.create(req("Old", 1, false, "java")).id();
        var updated = svc.update(id, req("New", 3, true, "sql"));
        assertEquals("New", updated.title());
        assertEquals(3, updated.level());
        assertTrue(updated.free());
        assertEquals(List.of("sql"), updated.skillIds());
        assertEquals(1, store.size());
        assertEquals(404, status(() -> svc.update("missing", req("X", 1, true, "java"))));
    }

    @Test
    void failedUpdateLeavesTheStoredCourseUntouched() {
        String id = svc.create(req("Keep", 1, false, "java")).id();
        assertEquals(400, status(() -> svc.update(id, req("Changed", 5, true, "ghost"))));
        assertEquals("Keep", ((Course) store.get(id)).getTitle());
    }

    @Test
    void deleteRemovesTheCourseAndUnknownIdIs404() {
        String id = svc.create(req("Gone", 1, true, "java")).id();
        svc.delete(id);
        assertTrue(store.isEmpty());
        assertEquals(404, status(() -> svc.delete(id)));
    }

    @Test
    void suggestIsEmptyWithoutGaps() {
        assertTrue(svc.suggest(null).isEmpty());
        assertTrue(svc.suggest(List.of()).isEmpty());
    }

    @Test
    void suggestPutsTheHighestPrioritySkillFirstAndKeepsSkillsWithoutCourses() {
        svc.create(req("Java 1", 2, true, "java"));
        List<CourseSuggestion> out = svc.suggest(List.of(gap("git", 0, 5), gap("java", 1, 20)));
        assertEquals(List.of("java", "git"), out.stream().map(CourseSuggestion::skillId).toList());
        assertEquals(1, out.get(0).courses().size());
        assertTrue(out.get(1).courses().isEmpty(), "a skill with no course is still reported");
    }

    @Test
    void suggestPrefersFreeThenTheLevelClosestToTheNextLevel() {
        svc.create(req("Paid exact", 2, false, "java"));
        svc.create(req("Free far", 5, true, "java"));
        svc.create(req("Free near", 2, true, "java"));
        var titles = svc.suggest(List.of(gap("java", 1, 10))).get(0).courses().stream().map(c -> c.title()).toList();
        assertEquals(List.of("Free near", "Free far", "Paid exact"), titles);
    }

    @Test
    void suggestReturnsAtMostThreeCoursesPerSkillAndOnlyForThatSkill() {
        for (int i = 0; i < 5; i++) svc.create(req("Java " + i, 2, true, "java"));
        svc.create(req("SQL only", 2, true, "sql"));
        var java = svc.suggest(List.of(gap("java", 1, 10))).get(0);
        assertEquals(CourseService.COURSES_PER_SKILL, java.courses().size());
        assertTrue(java.courses().stream().noneMatch(c -> c.title().equals("SQL only")));
    }

    @Test
    void suggestNeverTargetsAboveLevelFive() {
        svc.create(req("Expert", 5, true, "java"));
        svc.create(req("Beginner", 1, true, "java"));
        var titles = svc.suggest(List.of(gap("java", 5, 10))).get(0).courses().stream().map(c -> c.title()).toList();
        assertEquals("Expert", titles.get(0));
    }
}
