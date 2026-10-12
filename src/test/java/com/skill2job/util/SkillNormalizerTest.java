package com.skill2job.util;

import com.skill2job.entity.Skill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillNormalizerTest {

    private final List<Skill> skills = List.of(
            new Skill("React", "Framework", List.of("ReactJS", "React.js")),
            new Skill("C++", "Programming", List.of("cpp")),
            new Skill("C", "Programming", List.of()),
            new Skill("Spring Boot", "Framework", List.of("SpringBoot")));

    private String resolved(String text) {
        return SkillNormalizer.resolve(text, skills).map(Skill::getName).orElse(null);
    }

    @Test void keyRemovesPunctuationAndCase() { assertEquals("reactjs", SkillNormalizer.key(" React.JS ")); }
    @Test void keyKeepsPlusAndHash() { assertEquals("c++", SkillNormalizer.key("C++")); assertEquals("c#", SkillNormalizer.key("C#")); }
    @Test void keyOfNullIsEmpty() { assertEquals("", SkillNormalizer.key(null)); }
    @Test void resolvesExactName() { assertEquals("React", resolved("React")); }
    @Test void resolvesNameIgnoringCaseAndSpaces() { assertEquals("React", resolved("  react ")); }
    @Test void resolvesAlias() { assertEquals("React", resolved("ReactJS")); }
    @Test void resolvesDottedAlias() { assertEquals("React", resolved("react.js")); }
    @Test void resolvesSpacedName() { assertEquals("Spring Boot", resolved("springboot")); }
    @Test void cAndCppAreDifferent() { assertEquals("C", resolved("c")); assertEquals("C++", resolved("c++")); }
    @Test void unknownIsEmpty() { assertTrue(SkillNormalizer.resolve("Vue", skills).isEmpty()); }
    @Test void blankIsEmpty() { assertTrue(SkillNormalizer.resolve("  ", skills).isEmpty()); }
}
