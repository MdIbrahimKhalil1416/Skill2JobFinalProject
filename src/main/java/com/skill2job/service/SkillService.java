package com.skill2job.service;

import com.skill2job.audit.Audited;
import com.skill2job.dto.SkillRequest;
import com.skill2job.dto.SkillResponse;
import com.skill2job.entity.Job;
import com.skill2job.entity.Skill;
import com.skill2job.entity.StudentProfile;
import com.skill2job.exception.ApiException;
import com.skill2job.repository.JobRepository;
import com.skill2job.repository.StudentProfileRepository;
import com.skill2job.repository.SkillRepository;
import com.skill2job.util.SkillNormalizer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SkillService {

    public static final String CACHE = "skills";

    private final SkillRepository skillRepository;
    private final JobRepository jobRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final MongoTemplate mongoTemplate;

    public SkillService(SkillRepository skillRepository, JobRepository jobRepository,
                        StudentProfileRepository studentProfileRepository, MongoTemplate mongoTemplate) {
        this.skillRepository = skillRepository;
        this.jobRepository = jobRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /** All skills sorted by name. Cached in memory because it is read on almost every page. */
    @Cacheable(CACHE)
    public List<SkillResponse> listAll() {
        return skillRepository.findAll(Sort.by("name")).stream().map(SkillResponse::from).toList();
    }

    /** Search by name, category or alias. An exact alias match ("reactjs") is returned first. */
    public List<SkillResponse> search(String q) {
        List<Skill> all = skillRepository.findAll(Sort.by("name"));
        if (q == null || q.isBlank()) {
            return all.stream().map(SkillResponse::from).toList();
        }
        String key = SkillNormalizer.key(q);
        Set<Skill> result = new LinkedHashSet<>();
        SkillNormalizer.resolve(q, all).ifPresent(result::add);
        for (Skill s : all) {
            boolean hit = SkillNormalizer.key(s.getName()).contains(key)
                    || SkillNormalizer.key(s.getCategory()).contains(key)
                    || s.getAliases().stream().anyMatch(a -> SkillNormalizer.key(a).contains(key));
            if (hit) {
                result.add(s);
            }
        }
        return result.stream().map(SkillResponse::from).toList();
    }

    public Optional<Skill> resolve(String text) {
        return SkillNormalizer.resolve(text, skillRepository.findAll());
    }

    public Skill getEntity(String id) {
        return skillRepository.findById(id).orElseThrow(() -> ApiException.notFound("Skill not found."));
    }

    public Map<String, Skill> getMapByIds(Collection<String> ids) {
        return skillRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity(), (a, b) -> a));
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Audited(action = "SKILL_CREATE", targetType = "SKILL", detailArg = 0)
    public SkillResponse create(SkillRequest req) {
        Skill skill = new Skill();
        apply(skill, req);
        return SkillResponse.from(skillRepository.save(skill));
    }

    @CacheEvict(value = CACHE, allEntries = true)
    @Audited(action = "SKILL_UPDATE", targetType = "SKILL", targetArg = 0, detailArg = 1)
    public SkillResponse update(String id, SkillRequest req) {
        Skill skill = getEntity(id);
        String oldName = skill.getName();
        apply(skill, req);
        Skill saved = skillRepository.save(skill);
        if (!saved.getName().equals(oldName)) {
            renameEmbeddedCopies(saved.getId(), saved.getName());
        }
        return SkillResponse.from(saved);
    }

    /** Jobs and student profiles keep a copy of the skill name; keep those copies in sync after a rename. */
    private void renameEmbeddedCopies(String skillId, String newName) {
        Query jobQuery = new Query(Criteria.where("requiredSkills.skillId").is(skillId));
        for (Job job : mongoTemplate.find(jobQuery, Job.class)) {
            job.getRequiredSkills().stream()
                    .filter(rs -> skillId.equals(rs.getSkillId()))
                    .forEach(rs -> rs.setSkillName(newName));
            jobRepository.save(job);
        }
        Query profileQuery = new Query(Criteria.where("skills.skillId").is(skillId));
        for (StudentProfile profile : mongoTemplate.find(profileQuery, StudentProfile.class)) {
            profile.getSkills().stream()
                    .filter(ss -> skillId.equals(ss.getSkillId()))
                    .forEach(ss -> ss.setSkillName(newName));
            studentProfileRepository.save(profile);
        }
    }

    /** A skill that is used by a job or a student cannot be deleted (keeps data consistent). */
    @CacheEvict(value = CACHE, allEntries = true)
    @Audited(action = "SKILL_DELETE", targetType = "SKILL", targetArg = 0)
    public void delete(String id) {
        Skill skill = getEntity(id);
        if (jobRepository.existsBySkillId(id) || studentProfileRepository.existsBySkillId(id)) {
            throw ApiException.conflict("\"" + skill.getName() + "\" is used by jobs or students and cannot be deleted.");
        }
        skillRepository.delete(skill);
    }

    /** Checks that the new name/aliases do not clash with another skill. */
    private void apply(Skill skill, SkillRequest req) {
        String name = req.name().trim();
        List<String> aliases = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        seenKeys.add(SkillNormalizer.key(name));
        if (req.aliases() != null) {
            for (String a : req.aliases()) {
                String alias = a.trim();
                if (!alias.isEmpty() && seenKeys.add(SkillNormalizer.key(alias))) {
                    aliases.add(alias);
                }
            }
        }
        if (SkillNormalizer.key(name).isEmpty()) {
            throw ApiException.badRequest("Skill name must contain letters or digits.");
        }

        List<Skill> others = skillRepository.findAll().stream()
                .filter(s -> !s.getId().equals(skill.getId()))
                .toList();
        for (String k : seenKeys) {
            for (Skill other : others) {
                boolean clash = SkillNormalizer.key(other.getName()).equals(k)
                        || other.getAliases().stream().anyMatch(a -> SkillNormalizer.key(a).equals(k));
                if (clash) {
                    throw ApiException.conflict("This name or alias already belongs to skill \"" + other.getName() + "\".");
                }
            }
        }
        skill.setName(name);
        skill.setCategory(req.category().trim());
        skill.setAliases(aliases);
    }
}
