package com.skill2job.controller;

import com.skill2job.audit.AuditService;
import com.skill2job.dto.PageResponse;
import com.skill2job.enums.ReviewStatus;
import com.skill2job.mock.MockInterviewDtos;
import com.skill2job.mock.MockInterviewService;
import com.skill2job.quiz.QuizDtos;
import com.skill2job.quiz.QuizService;
import com.skill2job.reviews.CompanyReviewDtos;
import com.skill2job.reviews.CompanyReviewService;
import com.skill2job.service.AdminPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Admin: question banks, review moderation, audit log and payments overview. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin content")
public class AdminContentController {

    private final QuizService quizService;
    private final MockInterviewService mockInterviewService;
    private final CompanyReviewService reviewService;
    private final AuditService auditService;
    private final AdminPaymentService adminPaymentService;

    public AdminContentController(QuizService quizService, MockInterviewService mockInterviewService,
                                  CompanyReviewService reviewService, AuditService auditService,
                                  AdminPaymentService adminPaymentService) {
        this.quizService = quizService;
        this.mockInterviewService = mockInterviewService;
        this.reviewService = reviewService;
        this.auditService = auditService;
        this.adminPaymentService = adminPaymentService;
    }

    // ---- skill-test questions
    @GetMapping("/quiz-questions")
    @Operation(summary = "Skill-test questions (with answers), optionally for one skill")
    public List<QuizDtos.QuestionAdminView> quizQuestions(@RequestParam(required = false) String skillId) {
        return quizService.listForAdmin(skillId);
    }

    @PostMapping("/quiz-questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuizDtos.QuestionAdminView createQuizQuestion(@Valid @RequestBody QuizDtos.QuestionRequest request) {
        return quizService.create(request);
    }

    @PutMapping("/quiz-questions/{id}")
    public QuizDtos.QuestionAdminView updateQuizQuestion(@PathVariable String id, @Valid @RequestBody QuizDtos.QuestionRequest request) {
        return quizService.update(id, request);
    }

    @DeleteMapping("/quiz-questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteQuizQuestion(@PathVariable String id) {
        quizService.delete(id);
    }

    // ---- mock-interview questions
    @GetMapping("/interview-questions")
    @Operation(summary = "Mock-interview questions, optionally for one skill")
    public List<MockInterviewDtos.QuestionAdminView> interviewQuestions(@RequestParam(required = false) String skillId) {
        return mockInterviewService.listForAdmin(skillId);
    }

    @PostMapping("/interview-questions")
    @ResponseStatus(HttpStatus.CREATED)
    public MockInterviewDtos.QuestionAdminView createInterviewQuestion(@Valid @RequestBody MockInterviewDtos.QuestionRequest request) {
        return mockInterviewService.create(request);
    }

    @PutMapping("/interview-questions/{id}")
    public MockInterviewDtos.QuestionAdminView updateInterviewQuestion(@PathVariable String id,
                                                                       @Valid @RequestBody MockInterviewDtos.QuestionRequest request) {
        return mockInterviewService.update(id, request);
    }

    @DeleteMapping("/interview-questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInterviewQuestion(@PathVariable String id) {
        mockInterviewService.delete(id);
    }

    // ---- review moderation
    @GetMapping("/reviews")
    @Operation(summary = "Company reviews by status (PENDING = moderation queue); no status = latest 50")
    public List<CompanyReviewDtos.FullReview> reviews(@RequestParam(required = false) ReviewStatus status) {
        return reviewService.forAdmin(status);
    }

    @PatchMapping("/reviews/{id}")
    @Operation(summary = "Approve or reject a review")
    public CompanyReviewDtos.FullReview moderate(@PathVariable String id, @Valid @RequestBody CompanyReviewDtos.ModerationRequest request) {
        return reviewService.moderate(id, request);
    }

    @GetMapping("/pending-counts")
    @Operation(summary = "Badge counts for the admin tabs")
    public Map<String, Long> pendingCounts() {
        return Map.of("reviews", reviewService.pendingCount());
    }

    // ---- audit log
    @GetMapping("/audit-logs")
    @Operation(summary = "Audit log, newest first, with optional filters")
    public PageResponse<AuditService.AuditLogResponse> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return auditService.search(action, actor, success, from, to, page, size);
    }

    @GetMapping("/audit-logs/actions")
    @Operation(summary = "Distinct audit action names (for the filter)")
    public List<String> auditActions() {
        return auditService.actions();
    }

    // ---- payments
    @GetMapping("/payments/summary")
    @Operation(summary = "Revenue totals, monthly revenue and recent transactions")
    public AdminPaymentService.Summary payments() {
        return adminPaymentService.summary();
    }
}
