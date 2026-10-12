package com.skill2job.service;

import com.skill2job.dto.JobTemplateRequest;
import com.skill2job.dto.JobTemplateResponse;
import com.skill2job.dto.RequiredSkillRequest;
import com.skill2job.entity.JobTemplate;
import com.skill2job.entity.RequiredSkill;
import com.skill2job.entity.Skill;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.JobTemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reusable skill-set templates an employer saves to quickly prefill future job posts. */
@Service
public class JobTemplateService {

    private final JobTemplateRepository repository;
    private final SkillService skillService;

    public JobTemplateService(JobTemplateRepository repository, SkillService skillService) {
        this.repository = repository;
        this.skillService = skillService;
    }

    public List<JobTemplateResponse> mine(String employerId) {
        return repository.findByEmployerIdOrderByCreatedAtDesc(employerId).stream()
                .map(JobTemplateResponse::from).toList();
    }

    public JobTemplateResponse create(String employerId, JobTemplateRequest req) {
        Set<String> seen = new HashSet<>();
        for (RequiredSkillRequest rs : req.requiredSkills()) {
            if (!seen.add(rs.skillId())) {
                throw ApiException.badRequest("The same skill was added twice.");
            }
        }
        Map<String, Skill> skills = skillService.getMapByIds(seen);
        List<RequiredSkill> required = new ArrayList<>();
        for (RequiredSkillRequest rs : req.requiredSkills()) {
            Skill skill = skills.get(rs.skillId());
            if (skill == null) {
                throw ApiException.badRequest("Unknown skill id: " + rs.skillId());
            }
            required.add(new RequiredSkill(skill.getId(), skill.getName(), rs.requiredLevel(), rs.weight(), rs.mandatory()));
        }

        JobTemplate t = new JobTemplate();
        t.setEmployerId(employerId);
        t.setName(req.name().trim());
        t.setTitle(req.title().trim());
        t.setDescription(req.description() == null ? null : req.description().trim());
        t.setType(req.type());
        t.setLocation(req.location().trim());
        t.setRequiredSkills(required);
        t.setCreatedAt(Instant.now());
        return JobTemplateResponse.from(repository.save(t));
    }

    public void delete(String employerId, String id) {
        JobTemplate t = repository.findById(id).orElseThrow(() -> ApiException.notFound("Template not found."));
        if (!t.getEmployerId().equals(employerId)) {
            throw ApiException.forbidden("You can only manage your own templates.");
        }
        repository.delete(t);
    }
}
