package com.skill2job.dto;

import com.skill2job.entity.Skill;

import java.util.List;

public record SkillResponse(String id, String name, String category, List<String> aliases) {
    public static SkillResponse from(Skill s) {
        return new SkillResponse(s.getId(), s.getName(), s.getCategory(), List.copyOf(s.getAliases()));
    }
}
