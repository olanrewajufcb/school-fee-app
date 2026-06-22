package com.fee.app.schoolfeeapp.result.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.result.dto.request.*;
import com.fee.app.schoolfeeapp.result.dto.response.*;
import com.fee.app.schoolfeeapp.result.service.ResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @PutMapping("/ca-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CaConfigResponse>>> configureCa(
            @Valid @RequestBody CaConfigRequest request) {
        return resultService.configureCa(request)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/ca-config")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<CaConfigResponse>>> getCaConfig() {
        return resultService.getCaConfig()
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PostMapping("/ca-scores")
    @PreAuthorize("hasRole('TEACHER')")
    public Mono<ResponseEntity<ApiResponse<CaScoreResponse>>> enterCaScores(
            @Valid @RequestBody CaScoreRequest request) {
        return resultService.enterCaScores(request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r)));
    }

    @PostMapping("/exam-scores")
    @PreAuthorize("hasRole('TEACHER')")
    public Mono<ResponseEntity<ApiResponse<ExamScoreResponse>>> enterExamScores(
            @Valid @RequestBody ExamScoreRequest request) {
        return resultService.enterExamScores(request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r)))
                .doOnSuccess(response -> log.info("Scores successfully returned {}", response));
    }

    @PutMapping("/scores/{scoreId}")
    @PreAuthorize("hasRole('TEACHER')")
    public Mono<ResponseEntity<ApiResponse<UpdateScoreResponse>>> updateScore(
            @PathVariable UUID scoreId, @Valid @RequestBody UpdateScoreRequest request) {
        return resultService.updateScore(scoreId, request)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/students/{studentId}/term/{termId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<ApiResponse<StudentResultResponse>>> getStudentResult(
            @PathVariable UUID studentId, @PathVariable UUID termId) {
        return resultService.getStudentResult(studentId, termId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/students/{studentId}/published-terms")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<List<PublishedTermResultResponse>>>> getPublishedStudentResults(
            @PathVariable UUID studentId) {
        return resultService.getPublishedStudentResults(studentId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/classes/{classId}/term/{termId}/result-sheet")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<ClassResultSheetResponse>>> getClassResultSheet(
            @PathVariable UUID classId, @PathVariable UUID termId) {
        return resultService.getClassResultSheet(classId, termId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/classes/{classId}/subjects")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<SubjectLookupResponse>>>> getSubjectsForClass(
            @PathVariable UUID classId) {
        return resultService.getSubjectsForClass(classId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/ca-components")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<CaComponentLookupResponse>>>> getCaComponents() {
        return resultService.getCaComponents()
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/terms/{termId}/exams")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<ExamLookupResponse>>>> getExamsForTerm(
            @PathVariable UUID termId) {
        return resultService.getExamsForTerm(termId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/my-children/current")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<List<MyChildResultResponse>>>> getMyChildrenResults() {
        return resultService.getMyChildrenResults()
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PostMapping("/rankings/recompute")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> recomputeRankings(
            @Valid @RequestBody RecomputeRankingsRequest request) {
        return resultService.recomputeRankings(request.classId(), request.termId(), null)
                .map(rows -> ResponseEntity.ok(ApiResponse.success(Map.of(
                        "message", rows + " students ranked"))));
    }

    @PostMapping("/report-cards")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<ReportCardJobResponse>>> generateReportCards(
            @Valid @RequestBody ReportCardRequest request) {
        return resultService.generateReportCards(request)
                .map(r -> ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(r)));
    }

    @GetMapping("/report-cards/jobs/{jobId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<ReportCardJobResponse>>> getReportCardJob(
            @PathVariable UUID jobId) {
        return resultService.getReportCardJobStatus(jobId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PutMapping("/report-cards/{studentId}/term/{termId}/teacher-comment")
    @PreAuthorize("hasRole('TEACHER')")
    public Mono<ResponseEntity<ApiResponse<ReportCommentResponse>>> addTeacherComment(
            @PathVariable UUID studentId, @PathVariable UUID termId,
            @Valid @RequestBody CommentRequest request) {
        return resultService.addTeacherComment(studentId, termId, request.comment())
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PutMapping("/report-cards/{studentId}/term/{termId}/principal-comment")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<ReportCommentResponse>>> addPrincipalComment(
            @PathVariable UUID studentId, @PathVariable UUID termId,
            @Valid @RequestBody CommentRequest request) {
        return resultService.addPrincipalComment(studentId, termId, request.comment())
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PutMapping("/terms/{termId}/publish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PublishResultResponse>>> publishResults(
            @PathVariable UUID termId) {
        return resultService.publishResults(termId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PutMapping("/terms/{termId}/unpublish")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PublishResultResponse>>> unpublishResults(
            @PathVariable UUID termId) {
        return resultService.unpublishResults(termId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PutMapping("/grading-rules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<GradingRuleResponse>>> configureGradingRules(
            @Valid @RequestBody GradingRuleRequest request) {
        return resultService.configureGradingRules(request)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @GetMapping("/grading-rules")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<GradingRuleResponse>>> getGradingRules() {
        return resultService.getGradingRules()
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    // In ResultController.java

    /**
     * GET /api/v1/results/students/{studentId}/term/{termId}/download
     * Download a single student's result as PDF.
     */
    @GetMapping("/students/{studentId}/term/{termId}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<DataBuffer>> downloadStudentResult(
            @PathVariable UUID studentId,
            @PathVariable UUID termId) {
        return resultService.downloadStudentResultPdf(studentId, termId)
                .map(pdfData -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"result-" + studentId + "-" + termId + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdfData));
    }

    /**
     * POST /api/v1/results/students/{studentId}/term/{termId}/share
     * Share a student's result via SMS, WhatsApp, or Email.
     */
    @PostMapping("/students/{studentId}/term/{termId}/share")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<ShareResultResponse>>> shareStudentResult(
            @PathVariable UUID studentId,
            @PathVariable UUID termId,
            @Valid @RequestBody ShareResultRequest request) {
        return resultService.shareStudentResult(studentId, termId, request)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }
}
