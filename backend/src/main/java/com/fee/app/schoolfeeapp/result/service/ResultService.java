package com.fee.app.schoolfeeapp.result.service;

import com.fee.app.schoolfeeapp.result.dto.request.*;
import com.fee.app.schoolfeeapp.result.dto.response.*;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface ResultService {

    // CA Configuration
    Mono<CaConfigResponse> configureCa(CaConfigRequest request);
    Mono<CaConfigResponse> getCaConfig();

    // Score Entry
    Mono<CaScoreResponse> enterCaScores(CaScoreRequest request);
    Mono<ExamScoreResponse> enterExamScores(ExamScoreRequest request);
    Mono<UpdateScoreResponse> updateScore(UUID scoreId, UpdateScoreRequest request);

    // Results Viewing
    Mono<StudentResultResponse> getStudentResult(UUID studentId, UUID termId);
    Mono<ClassResultSheetResponse> getClassResultSheet(UUID classId, UUID termId);
    Mono<List<MyChildResultResponse>> getMyChildrenResults();
    Mono<List<PublishedTermResultResponse>> getPublishedStudentResults(UUID studentId);

    // Rankings
    Mono<Integer> recomputeRankings(UUID classId, UUID termId, UUID schoolId);

    // Report Cards
    Mono<ReportCardJobResponse> generateReportCards(ReportCardRequest request);
    Mono<ReportCardJobResponse> getReportCardJobStatus(UUID jobId);

    // Comments
    Mono<ReportCommentResponse> addTeacherComment(UUID studentId, UUID termId, String comment);
    Mono<ReportCommentResponse> addPrincipalComment(UUID studentId, UUID termId, String comment);

    // Publication
    Mono<PublishResultResponse> publishResults(UUID termId);
    Mono<PublishResultResponse> unpublishResults(UUID termId);

    // Grading Rules
    Mono<GradingRuleResponse> configureGradingRules(GradingRuleRequest request);
    Mono<GradingRuleResponse> getGradingRules();

    // Lookups
    Mono<List<SubjectLookupResponse>> getSubjectsForClass(UUID classId);
    Mono<List<CaComponentLookupResponse>> getCaComponents();
    Mono<List<ExamLookupResponse>> getExamsForTerm(UUID termId);

    // In ResultService.java

    /**
     * Download a single student's result as PDF.
     */
    Mono<DataBuffer> downloadStudentResultPdf(UUID studentId, UUID termId);

    /**
     * Share a student's result via SMS, WhatsApp, or Email.
     */
    Mono<ShareResultResponse> shareStudentResult(UUID studentId, UUID termId, ShareResultRequest request);
}
