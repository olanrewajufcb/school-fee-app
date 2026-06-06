package com.fee.app.schoolfeeapp.result.service;

import com.fee.app.schoolfeeapp.result.dto.request.CaConfigRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ExamScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.request.GradingRuleRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ReportCardRequest;
import com.fee.app.schoolfeeapp.result.dto.response.*;
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
}