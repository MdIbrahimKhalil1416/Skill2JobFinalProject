package com.skill2job.controller;

import com.skill2job.quiz.QuizDtos;
import com.skill2job.quiz.QuizService;
import com.skill2job.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quizzes")
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Skill tests")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @GetMapping
    @Operation(summary = "Skills that have a test, with my verified level and retake time")
    public List<QuizDtos.SkillQuizSummary> overview(@AuthenticationPrincipal AuthUser user) {
        return quizService.overview(user.id());
    }

    @PostMapping("/{skillId}/start")
    @Operation(summary = "Start (or resume) a skill test - questions come without answers")
    public QuizDtos.StartResponse start(@AuthenticationPrincipal AuthUser user, @PathVariable String skillId) {
        return quizService.start(user.id(), skillId);
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @Operation(summary = "Submit answers; passing (60%+) verifies the skill at the level the score proves")
    public QuizDtos.ResultResponse submit(@AuthenticationPrincipal AuthUser user, @PathVariable String attemptId,
                                          @Valid @RequestBody QuizDtos.SubmitRequest request) {
        return quizService.submit(user.id(), attemptId, request);
    }

    @GetMapping("/attempts")
    @Operation(summary = "My recent test attempts")
    public List<QuizDtos.AttemptView> history(@AuthenticationPrincipal AuthUser user) {
        return quizService.history(user.id());
    }
}
