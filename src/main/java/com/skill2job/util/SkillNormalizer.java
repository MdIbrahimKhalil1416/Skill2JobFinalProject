package com.skill2job.util;

import com.skill2job.entity.Skill;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

/**
 * Maps free text like "ReactJS", "React.js" or " react " to one canonical Skill,
 * using the skill name and its aliases.
 */
public final class SkillNormalizer {

    private SkillNormalizer() { }

    /**
     * Lower-case and drop everything except letters, digits, '+' and '#'.
     * "React.js" -> "reactjs", "C++" -> "c++", "Spring Boot" -> "springboot".
     */
    public static String key(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '+' || c == '#') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Finds the skill whose name or any alias has the same key as the text. */
    public static Optional<Skill> resolve(String text, Collection<Skill> skills) {
        String k = key(text);
        if (k.isEmpty() || skills == null) {
            return Optional.empty();
        }
        for (Skill s : skills) {
            if (key(s.getName()).equals(k)) {
                return Optional.of(s);
            }
        }
        for (Skill s : skills) {
            for (String alias : s.getAliases()) {
                if (key(alias).equals(k)) {
                    return Optional.of(s);
                }
            }
        }
        return Optional.empty();
    }
}
