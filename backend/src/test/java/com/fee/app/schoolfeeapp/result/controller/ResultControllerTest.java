package com.fee.app.schoolfeeapp.result.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.result.dto.request.CaConfigRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ExamScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.request.RecomputeRankingsRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ReportCardRequest;
import com.fee.app.schoolfeeapp.result.dto.request.CommentRequest;
import com.fee.app.schoolfeeapp.result.dto.response.CaConfigResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ReportCardJobResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ReportCommentResponse;
import com.fee.app.schoolfeeapp.result.dto.response.CaScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.response.CaScoreResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ExamScoreResponse;
import com.fee.app.schoolfeeapp.result.dto.response.MyChildResultResponse;
import com.fee.app.schoolfeeapp.result.dto.response.PublishResultResponse;
import com.fee.app.schoolfeeapp.result.dto.response.StudentResultResponse;
import com.fee.app.schoolfeeapp.result.dto.response.UpdateScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.response.UpdateScoreResponse;
import com.fee.app.schoolfeeapp.result.service.ResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultControllerTest {

    @Mock
    private ResultService resultService;

    @InjectMocks
    private ResultController resultController;

    @Test
    @DisplayName("Should configure CA successfully")
    void shouldConfigureCaSuccessfully() {
        CaConfigRequest request = validRequest();
        CaConfigResponse serviceResponse = new CaConfigResponse(
                2, 60.0, "CA configuration updated");
        when(resultService.configureCa(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.configureCa(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<CaConfigResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).configureCa(request);
    }

    @Test
    @DisplayName("Should get CA config successfully")
    void shouldGetCaConfigSuccessfully() {
        CaConfigResponse serviceResponse = new CaConfigResponse(
                2, 60.0, "Current CA configuration");
        when(resultService.getCaConfig()).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.getCaConfig())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isSuccess()).isTrue();
                    assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).getCaConfig();
    }

    @Test
    @DisplayName("Should propagate CA config errors")
    void shouldPropagateCaConfigErrors() {
        CaConfigRequest request = validRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "CA_CONFIG_IN_USE",
                "CA configuration already has recorded scores and cannot be replaced");
        when(resultService.configureCa(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.configureCa(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).configureCa(request);
    }

    private CaConfigRequest validRequest() {
        return new CaConfigRequest(
                List.of(
                        new CaConfigRequest.CaComponentRequest("First Test", 20, 20, 1),
                        new CaConfigRequest.CaComponentRequest("Second Test", 20, 20, 2)),
                60);
    }

    @Test
    @DisplayName("Should enter CA scores successfully")
    void shouldEnterCaScoresSuccessfully() {
        CaScoreRequest request = new CaScoreRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 20, List.of());
        CaScoreResponse serviceResponse = new CaScoreResponse(
                UUID.randomUUID(), "classId", "subjectId", "caComponentId", 0, "CA scores recorded");
        when(resultService.enterCaScores(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.enterCaScores(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<CaScoreResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).enterCaScores(request);
    }

    @Test
    @DisplayName("Should enter exam scores successfully")
    void shouldEnterExamScoresSuccessfully() {
        ExamScoreRequest request = new ExamScoreRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, List.of());
        ExamScoreResponse serviceResponse = new ExamScoreResponse(
                UUID.randomUUID(), "classId", "subjectId", 0, 0, null, null, null, "Exam scores recorded");
        when(resultService.enterExamScores(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.enterExamScores(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<ExamScoreResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).enterExamScores(request);
    }

    @Test
    @DisplayName("Should update score successfully")
    void shouldUpdateScoreSuccessfully() {
        UUID scoreId = UUID.randomUUID();
        UpdateScoreRequest request = new UpdateScoreRequest(BigDecimal.valueOf(70), "Correction");
        UpdateScoreResponse serviceResponse = new UpdateScoreResponse(
                scoreId,
                BigDecimal.valueOf(70),
                BigDecimal.valueOf(55),
                Instant.parse("2026-06-06T10:00:00Z"));
        when(resultService.updateScore(scoreId, request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.updateScore(scoreId, request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<UpdateScoreResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).updateScore(scoreId, request);
    }

    @Test
    @DisplayName("Should get student result successfully")
    void shouldGetStudentResultSuccessfully() {
        UUID studentId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        StudentResultResponse serviceResponse = new StudentResultResponse(
                new StudentResultResponse.StudentInfo(studentId, "GIS-001", "Test Student", "Basic 1A", 1, null),
                new StudentResultResponse.TermInfo(termId, "First Term", "2025/2026"),
                List.of(),
                new StudentResultResponse.ResultSummary(BigDecimal.ZERO, 0, BigDecimal.ZERO, null, BigDecimal.ZERO, 0, 0, 0),
                null,
                null,
                null,
                null);
        when(resultService.getStudentResult(studentId, termId)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.getStudentResult(studentId, termId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<StudentResultResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).getStudentResult(studentId, termId);
    }

    @Test
    @DisplayName("Should propagate score update errors")
    void shouldPropagateScoreUpdateErrors() {
        UUID scoreId = UUID.randomUUID();
        UpdateScoreRequest request = new UpdateScoreRequest(BigDecimal.valueOf(101), "Correction");
        SchoolFeeException expectedError = new SchoolFeeException("INVALID_SCORE", "Score must be between 0 and 100");
        when(resultService.updateScore(scoreId, request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.updateScore(scoreId, request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).updateScore(scoreId, request);
    }

    @Test
    @DisplayName("Should recompute rankings successfully")
    void shouldRecomputeRankingsSuccessfully() {
        UUID classId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        RecomputeRankingsRequest request = new RecomputeRankingsRequest(termId, classId);
        when(resultService.recomputeRankings(classId, termId, null)).thenReturn(Mono.just(2));

        StepVerifier.create(resultController.recomputeRankings(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, String>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(Map.of("message", "2 students ranked"));
                })
                .verifyComplete();

        verify(resultService).recomputeRankings(classId, termId, null);
    }

    @Test
    @DisplayName("Should get my children results successfully")
    void shouldGetMyChildrenResultsSuccessfully() {
        List<MyChildResultResponse> serviceResponse = List.of(
                new MyChildResultResponse(UUID.randomUUID(), null, null, null, null, null, List.of()));
        when(resultService.getMyChildrenResults()).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.getMyChildrenResults())
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<MyChildResultResponse>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).getMyChildrenResults();
    }

    @Test
    @DisplayName("Should propagate recompute rankings errors")
    void shouldPropagateRecomputeRankingsErrors() {
        UUID classId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        RecomputeRankingsRequest request = new RecomputeRankingsRequest(termId, classId);
        SchoolFeeException expectedError = new SchoolFeeException("CLASS_NOT_FOUND", "Class not found");
        when(resultService.recomputeRankings(classId, termId, null)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.recomputeRankings(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).recomputeRankings(classId, termId, null);
    }

    @Test
    @DisplayName("Should generate report cards successfully")
    void shouldGenerateReportCardsSuccessfully() {
        UUID termId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        ReportCardRequest request = new ReportCardRequest(
                termId,
                classId,
                List.of(UUID.randomUUID()),
                true,
                true,
                false,
                null,
                "PDF");
        ReportCardJobResponse serviceResponse = new ReportCardJobResponse(
                UUID.randomUUID(), "PROCESSING", 1, 0, 0, null, null, "Report card generation started");
        when(resultService.generateReportCards(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.generateReportCards(request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    ApiResponse<ReportCardJobResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).generateReportCards(request);
    }

    @Test
    @DisplayName("Should get report card job successfully")
    void shouldGetReportCardJobSuccessfully() {
        UUID jobId = UUID.randomUUID();
        ReportCardJobResponse serviceResponse = new ReportCardJobResponse(
                jobId, "COMPLETED", 2, 2, 0, null, Instant.now(), "Report cards generated");
        when(resultService.getReportCardJobStatus(jobId)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.getReportCardJob(jobId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<ReportCardJobResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).getReportCardJobStatus(jobId);
    }

    @Test
    @DisplayName("Should propagate report card job errors")
    void shouldPropagateReportCardJobErrors() {
        UUID jobId = UUID.randomUUID();
        SchoolFeeException expectedError = new SchoolFeeException("REPORT_CARD_JOB_NOT_FOUND", "Report card job not found");
        when(resultService.getReportCardJobStatus(jobId)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.getReportCardJob(jobId))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).getReportCardJobStatus(jobId);
    }

    @Test
    @DisplayName("Should add teacher comment successfully")
    void shouldAddTeacherCommentSuccessfully() {
        UUID studentId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        CommentRequest request = new CommentRequest("Excellent term performance");
        ReportCommentResponse serviceResponse = new ReportCommentResponse(
                studentId,
                termId,
                request.comment(),
                Instant.now());
        when(resultService.addTeacherComment(studentId, termId, request.comment()))
                .thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.addTeacherComment(studentId, termId, request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<ReportCommentResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).addTeacherComment(studentId, termId, request.comment());
    }

    @Test
    @DisplayName("Should add principal comment successfully")
    void shouldAddPrincipalCommentSuccessfully() {
        UUID studentId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        CommentRequest request = new CommentRequest("Promoted to next class");
        ReportCommentResponse serviceResponse = new ReportCommentResponse(
                studentId,
                termId,
                request.comment(),
                Instant.now());
        when(resultService.addPrincipalComment(studentId, termId, request.comment()))
                .thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.addPrincipalComment(studentId, termId, request))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<ReportCommentResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).addPrincipalComment(studentId, termId, request.comment());
    }

    @Test
    @DisplayName("Should propagate teacher comment errors")
    void shouldPropagateTeacherCommentErrors() {
        UUID studentId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        CommentRequest request = new CommentRequest("Any");
        SchoolFeeException expectedError = new SchoolFeeException("STUDENT_NOT_FOUND", "Student not found");
        when(resultService.addTeacherComment(studentId, termId, request.comment())).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.addTeacherComment(studentId, termId, request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).addTeacherComment(studentId, termId, request.comment());
    }

    @Test
    @DisplayName("Should propagate principal comment errors")
    void shouldPropagatePrincipalCommentErrors() {
        UUID studentId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        CommentRequest request = new CommentRequest("Any");
        SchoolFeeException expectedError = new SchoolFeeException("TERM_NOT_FOUND", "Term not found");
        when(resultService.addPrincipalComment(studentId, termId, request.comment())).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.addPrincipalComment(studentId, termId, request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).addPrincipalComment(studentId, termId, request.comment());
    }

    @Test
    @DisplayName("Should publish results successfully")
    void shouldPublishResultsSuccessfully() {
        UUID termId = UUID.randomUUID();
        PublishResultResponse serviceResponse = new PublishResultResponse(
                termId,
                "PUBLISHED",
                Instant.now(),
                UUID.randomUUID().toString(),
                "Results published. Parents can now view report cards.");
        when(resultService.publishResults(termId)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.publishResults(termId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<PublishResultResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).publishResults(termId);
    }

    @Test
    @DisplayName("Should unpublish results successfully")
    void shouldUnpublishResultsSuccessfully() {
        UUID termId = UUID.randomUUID();
        PublishResultResponse serviceResponse = new PublishResultResponse(
                termId,
                "UNPUBLISHED",
                Instant.now(),
                UUID.randomUUID().toString(),
                "Results unpublished. Teachers can now modify scores.");
        when(resultService.unpublishResults(termId)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(resultController.unpublishResults(termId))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<PublishResultResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(resultService).unpublishResults(termId);
    }

    @Test
    @DisplayName("Should propagate publish results errors")
    void shouldPropagatePublishResultsErrors() {
        UUID termId = UUID.randomUUID();
        SchoolFeeException expectedError = new SchoolFeeException("RESULTS_ALREADY_PUBLISHED", "Already published");
        when(resultService.publishResults(termId)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.publishResults(termId))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).publishResults(termId);
    }

    @Test
    @DisplayName("Should propagate unpublish results errors")
    void shouldPropagateUnpublishResultsErrors() {
        UUID termId = UUID.randomUUID();
        SchoolFeeException expectedError = new SchoolFeeException("RESULTS_NOT_PUBLISHED", "Not published");
        when(resultService.unpublishResults(termId)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(resultController.unpublishResults(termId))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(resultService).unpublishResults(termId);
    }
}
