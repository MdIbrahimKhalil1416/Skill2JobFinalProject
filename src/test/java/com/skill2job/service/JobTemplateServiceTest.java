package com.skill2job.service;

import com.skill2job.dto.JobTemplateRequest;
import com.skill2job.dto.RequiredSkillRequest;
import com.skill2job.entity.Skill;
import com.skill2job.enums.JobType;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.JobTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Employer job templates: validation of skills, trimming, ownership on delete. */
class JobTemplateServiceTest {

    final Map<String, Object> store = new LinkedHashMap<>();
    final JobTemplateService svc = new JobTemplateService(MapRepo.of(JobTemplateRepository.class, store),
            new SkillService(null, null, null, null) {
                @Override public Map<String, Skill> getMapByIds(java.util.Collection<String> ids) {
                    Map<String, Skill> m = new LinkedHashMap<>();
                    for (String id : ids) if (id.equals("java") || id.equals("sql")) { Skill s = new Skill(); s.setId(id); s.setName(id.toUpperCase()); m.put(id, s); }
                    return m;
                }
            });

    private static JobTemplateRequest req(RequiredSkillRequest... skills) {
        return new JobTemplateRequest(" Backend ", " Backend Intern ", null, JobType.INTERNSHIP, " Dhaka ", List.of(skills));
    }

    private static int status(Runnable r) {
        try { r.run(); return 200; } catch (ApiException e) { return e.getStatus().value(); }
    }

    @Test
    void createTrimsAndResolvesSkillNames() {
        var t = svc.create("e1", req(new RequiredSkillRequest("java", 3, 3, true)));
        assertEquals("Backend", t.name());
        assertEquals("Backend Intern", t.title());
        assertEquals("Dhaka", t.location());
        assertEquals(1, svc.mine("e1").size());
        assertEquals(0, svc.mine("e2").size(), "templates are private to the employer");
    }

    @Test
    void duplicateOrUnknownSkillsAreRejectedAndNothingIsSaved() {
        assertEquals(400, status(() -> svc.create("e1", req(new RequiredSkillRequest("java", 3, 3, true), new RequiredSkillRequest("java", 2, 2, false)))));
        assertEquals(400, status(() -> svc.create("e1", req(new RequiredSkillRequest("ghost", 3, 3, true)))));
        assertTrue(store.isEmpty());
    }

    @Test
    void onlyTheOwnerCanDeleteATemplate() {
        String id = svc.create("e1", req(new RequiredSkillRequest("sql", 2, 2, false))).id();
        assertEquals(403, status(() -> svc.delete("e2", id)));
        assertEquals(1, store.size());
        svc.delete("e1", id);
        assertTrue(store.isEmpty());
        assertEquals(404, status(() -> svc.delete("e1", id)));
    }
}
