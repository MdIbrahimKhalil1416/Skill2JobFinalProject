package com.skill2job.mock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerEvaluatorTest {

    private static final List<String> POINTS = List.of(
            "embed data read together", "reference large or unbounded data",
            "embed small bounded data", "reference data shared by many documents");

    @Test
    void stemsTreatWordFormsAsTheSame() {
        assertTrue(AnswerEvaluator.sameWord(AnswerEvaluator.stem("embedding"), AnswerEvaluator.stem("embed")));
        assertTrue(AnswerEvaluator.sameWord(AnswerEvaluator.stem("tests"), AnswerEvaluator.stem("test")));
        assertTrue(AnswerEvaluator.sameWord(AnswerEvaluator.stem("referencing"), AnswerEvaluator.stem("reference")));
    }

    @Test
    void shortTechTermsMustMatchExactly() {
        assertTrue(AnswerEvaluator.sameWord("jwt", "jwt"));
        assertTrue(!AnswerEvaluator.sameWord("sql", "sqlite"));
    }

    @Test
    void goodAnswerCoversMostPoints() {
        String answer = "I embed data that is always read together and is small and bounded, like an address. "
                + "When the data is large or unbounded, or shared by many documents, I store a reference instead.";
        AnswerEvaluator.Evaluation ev = AnswerEvaluator.evaluate(answer, POINTS);
        assertEquals(4, ev.covered().size());
        assertEquals(100.0, ev.coveragePercent(), 0.001);
    }

    @Test
    void emptyAnswerCoversNothing() {
        AnswerEvaluator.Evaluation ev = AnswerEvaluator.evaluate("", POINTS);
        assertEquals(0.0, ev.coveragePercent(), 0.001);
        assertTrue(ev.feedback().startsWith("No answer"));
    }

    @Test
    void partialAnswerListsMissingPoints() {
        AnswerEvaluator.Evaluation ev = AnswerEvaluator.evaluate("You should embed data that is read together.", POINTS);
        assertTrue(ev.covered().contains("embed data read together"));
        assertTrue(ev.missed().contains("reference large or unbounded data"));
        assertTrue(ev.coveragePercent() < 75);
    }

    @Test
    void sessionScoreWeightsCoverageSeventyPercent() {
        assertEquals(100.0, AnswerEvaluator.sessionScore(100, 5), 0.001);
        assertEquals(70.0, AnswerEvaluator.sessionScore(100, 0), 0.001);
        assertEquals(30.0, AnswerEvaluator.sessionScore(0, 5), 0.001);
    }
}
