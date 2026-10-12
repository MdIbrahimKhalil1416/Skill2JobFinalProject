package com.skill2job.mock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rule-based (not AI) feedback for a written interview answer: for each "key point" a good answer
 * should cover, checks whether enough of that point's keywords appear in the answer. Words are
 * lightly stemmed so "embedding"/"embedded"/"embed" or "tests"/"test" count as the same word.
 * Pure Java - unit tested in AnswerEvaluatorTest.
 */
public final class AnswerEvaluator {

    private static final Set<String> STOP = Set.of(
            "the", "and", "for", "are", "but", "not", "you", "your", "with", "this", "that", "from", "they", "them",
            "have", "has", "had", "was", "were", "will", "can", "into", "its", "it's", "our", "out", "use", "used",
            "using", "when", "what", "which", "who", "how", "why", "about", "than", "then", "there", "their", "also",
            "only", "more", "most", "very", "just", "like", "such", "each", "other", "some", "any", "all", "one",
            "two", "does", "did", "done", "may", "might", "should", "would", "could", "being", "been", "over",
            "under", "per", "via", "versus", "between", "within", "without", "both", "same", "many", "much");
    private static final String[] SUFFIXES = {"ations", "ation", "ions", "ion", "ings", "ing", "edly", "ed", "es", "s", "ly"};

    private AnswerEvaluator() { }

    /** Result for one answer. */
    public record Evaluation(List<String> covered, List<String> missed, double coveragePercent, String feedback) { }

    public static Evaluation evaluate(String answer, List<String> keyPoints) {
        List<String> covered = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        Set<String> answerStems = stems(answer);
        for (String point : keyPoints) {
            if (isCovered(answerStems, point)) {
                covered.add(point);
            } else {
                missed.add(point);
            }
        }
        double coverage = keyPoints.isEmpty() ? 0 : Math.round(covered.size() * 1000.0 / keyPoints.size()) / 10.0;
        return new Evaluation(covered, missed, coverage, feedback(answer, coverage, missed));
    }

    /** A point counts as covered when at least 60% of its keywords (minimum one) appear in the answer. */
    static boolean isCovered(Set<String> answerStems, String point) {
        Set<String> keywords = stems(point);
        if (keywords.isEmpty()) {
            return false;
        }
        int hits = 0;
        for (String k : keywords) {
            for (String a : answerStems) {
                if (sameWord(a, k)) {
                    hits++;
                    break;
                }
            }
        }
        int needed = Math.max(1, (int) Math.ceil(keywords.size() * 0.6));
        return hits >= needed;
    }

    static boolean sameWord(String a, String b) {
        if (a.length() < 4 || b.length() < 4) {
            return a.equals(b);
        }
        return a.startsWith(b) || b.startsWith(a);
    }

    static Set<String> stems(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#]+")) {
            if (raw.length() < 3 || STOP.contains(raw)) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    static String stem(String word) {
        String w = word;
        for (String suffix : SUFFIXES) {
            if (w.endsWith(suffix) && w.length() - suffix.length() >= 4) {
                w = w.substring(0, w.length() - suffix.length());
                break;
            }
        }
        return w.length() > 6 ? w.substring(0, 6) : w;
    }

    static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    static String feedback(String answer, double coverage, List<String> missed) {
        int words = wordCount(answer);
        if (words == 0) {
            return "No answer given. Even a short structured answer is better than skipping in a real interview.";
        }
        String missing = missed.isEmpty() ? "" : " Also mention: " + String.join("; ", missed) + ".";
        if (coverage >= 75) {
            return "Strong answer - you covered the main points." + (words < 25 ? " Add a concrete example to stand out." : "") + missing;
        }
        if (coverage >= 40) {
            return "Good start." + missing + (words < 25 ? " Try to expand your answer to 3-5 sentences." : "");
        }
        return "This answer misses most of what interviewers look for." + missing
                + " Structure it as: definition, how it works, an example.";
    }

    /** Combined readiness score: key-point coverage weighted 70%, self-rating (1-5) 30%. */
    public static double sessionScore(double averageCoverage, double averageSelfRating) {
        double self = Math.max(0, Math.min(5, averageSelfRating)) / 5.0 * 100.0;
        return Math.round((averageCoverage * 0.7 + self * 0.3) * 10.0) / 10.0;
    }
}
