package com.fee.app.schoolfeeapp.result.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLinkProjection;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import com.fee.app.schoolfeeapp.result.domain.*;
import com.fee.app.schoolfeeapp.result.dto.request.CaConfigRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ExamScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.request.GradingRuleRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ReportCardRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ReportCardTemplateRequest;
import com.fee.app.schoolfeeapp.result.dto.response.MyChildResultResponse;
import com.fee.app.schoolfeeapp.result.dto.response.PublishResultResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ReportCardJobResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ReportCommentResponse;
import com.fee.app.schoolfeeapp.result.dto.response.StudentResultResponse;
import com.fee.app.schoolfeeapp.result.dto.response.UpdateScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.response.CaScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.response.SubjectLookupResponse;
import com.fee.app.schoolfeeapp.result.dto.response.CaComponentLookupResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ExamLookupResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ClassResultSheetResponse;
import com.fee.app.schoolfeeapp.result.repository.*;
import com.fee.app.schoolfeeapp.result.service.ScoreComputationEngine;
import com.fee.app.schoolfeeapp.result.utils.ResultPdfGenerator;
import com.fee.app.schoolfeeapp.school.domain.*;
import com.fee.app.schoolfeeapp.school.repository.*;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import com.fee.app.schoolfeeapp.result.dto.request.ShareResultRequest;
import com.fee.app.schoolfeeapp.result.dto.request.TraitAssessmentRequest;
import com.fee.app.schoolfeeapp.result.dto.response.TraitAssessmentSaveResponse;
import com.fee.app.schoolfeeapp.result.dto.response.AssessmentTraitResponse;
import com.fee.app.schoolfeeapp.result.dto.response.TraitAssessmentValueResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceImplTest {

    @Mock
    private CaComponentRepository caComponentRepository;
    @Mock
    private CaScoreRepository caScoreRepository;
    @Mock
    private ScoreRepository scoreRepository;
    @Mock
    private FinalScoreRepository finalScoreRepository;
    @Mock
    private ClassRankingRepository rankingRepository;
    @Mock
    private GradeConfigRepository gradeConfigRepository;
    @Mock
    private ReportCommentRepository commentRepository;
    @Mock
    private PublishedResultRepository publishedResultRepository;
    @Mock
    private ScoreAuditLogRepository auditLogRepository;
    @Mock
    private ReportCardTemplateRepository reportCardTemplateRepository;
    @Mock
    private AssessmentTraitRepository assessmentTraitRepository;
    @Mock
    private StudentTraitAssessmentRepository studentTraitAssessmentRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private ClassSubjectRepository classSubjectRepository;
    @Mock
    private ExamRepository examRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ClassRepository classRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private TermRepository termRepository;
    @Mock
    private AcademicSessionRepository academicSessionRepository;
    @Mock
    private StudentGuardianRepository guardianRepository;
    @Mock
    private StudentGuardianLinkRepository guardianLinkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private ScoreComputationEngine computationEngine;

    @Mock
    private ResultPdfGenerator resultPdfGenerator;
    @Mock
    private SmsService smsService;

    private ResultServiceImpl resultService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SESSION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TERM_ID = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final UUID CLASS_ID = UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final UUID SUBJECT_ID = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb");
    private static final UUID EXAM_ID = UUID.fromString("99999999-aaaa-bbbb-cccc-dddddddddddd");
    private static final UUID COMPONENT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
    private static final UUID TEMPLATE_ID = UUID.fromString("12121212-3434-5656-7878-909090909090");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        resultService = new ResultServiceImpl(
                caComponentRepository,
                caScoreRepository,
                scoreRepository,
                finalScoreRepository,
                rankingRepository,
                gradeConfigRepository,
                commentRepository,
                publishedResultRepository,
                auditLogRepository,
                reportCardTemplateRepository,
                assessmentTraitRepository,
                studentTraitAssessmentRepository,
                subjectRepository,
                classSubjectRepository,
                examRepository,
                studentRepository,
                classRepository,
                schoolRepository,
                termRepository,
                academicSessionRepository,
                guardianRepository,
                guardianLinkRepository,
                userRepository,
                jwtUtils,
                transactionalOperator,
                computationEngine,
                resultPdfGenerator,
                smsService);

        org.mockito.Mockito.lenient().when(userRepository.findByKeycloakIdAndDeletedAtIsNull(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID kid = invocation.getArgument(0);
                    return Mono.just(User.builder().id(kid).keycloakId(kid).build());
                });
        org.mockito.Mockito.lenient().when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(any(UUID.class)))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(rankingRepository.findByClassIdAndTermIdOrderByAveragePercentageDesc(any(UUID.class), any(UUID.class)))
                .thenReturn(Flux.empty());
        org.mockito.Mockito.lenient()
                .when(studentTraitAssessmentRepository.findForStudentResult(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Flux.empty());
        org.mockito.Mockito.lenient()
                .when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        org.mockito.Mockito.lenient()
                .when(examRepository.findByTermId(any(UUID.class)))
                .thenReturn(Flux.empty());
        org.mockito.Mockito.lenient()
                .when(scoreRepository.findLatestByStudentIdAndSubjectIdAndTermIdAndSchoolId(any(UUID.class), any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(classSubjectRepository.findByClassIdAndSubjectIdAndSchoolIdAndIsActiveTrue(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.just(classSubject()));
        org.mockito.Mockito.lenient()
                .when(examRepository.findByIdAndSchoolIdAndTermId(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.just(exam()));
        org.mockito.Mockito.lenient()
                .when(computationEngine.computeFinalScores(any(UUID.class), any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(computationEngine.computeSubjectPositions(any(UUID.class), any(UUID.class)))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(computationEngine.computeClassRankings(any(UUID.class), any(UUID.class), any(UUID.class), any()))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(caComponentRepository.findBySchoolIdAndIsActiveTrue(any(UUID.class)))
                .thenReturn(Flux.empty());
        org.mockito.Mockito.lenient()
                .when(classSubjectRepository.findByClassIdAndIsActiveTrue(any(UUID.class)))
                .thenReturn(Flux.empty());
    }

    @Test
    @DisplayName("Should replace CA configuration transactionally")
    void shouldReplaceCaConfigurationTransactionally() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(school()));
        when(caScoreRepository.existsBySchoolIdAndActiveComponents(SCHOOL_ID)).thenReturn(Mono.just(false));
        when(caComponentRepository.deactivateActiveBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(2));
        when(caComponentRepository.insert(any(CaComponent.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        CaConfigRequest request = new CaConfigRequest(
                List.of(
                        new CaConfigRequest.CaComponentRequest(" Second Test ", 20, 20, 2),
                        new CaConfigRequest.CaComponentRequest("First Test", 10, 10, 1)),
                70);

        StepVerifier.create(resultService.configureCa(request))
                .assertNext(response -> {
                    assertThat(response.componentCount()).isEqualTo(2);
                    assertThat(response.examWeightPercentage()).isEqualTo(70.0);
                    assertThat(response.message()).isEqualTo("CA configuration updated");
                })
                .verifyComplete();

        ArgumentCaptor<CaComponent> captor = ArgumentCaptor.forClass(CaComponent.class);
        verify(caComponentRepository).deactivateActiveBySchoolId(SCHOOL_ID);
        verify(caComponentRepository, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<CaComponent> saved = captor.getAllValues();
        assertThat(saved).extracting(CaComponent::getName)
                .containsExactly("First Test", "Second Test");
        assertThat(saved).extracting(CaComponent::getSortOrder)
                .containsExactly(1, 2);
        assertThat(saved).allSatisfy(component -> {
            assertThat(component.getSchoolId()).isEqualTo(SCHOOL_ID);
            assertThat(component.isActive()).isTrue();
            assertThat(component.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should reject config changes when active CA components have scores")
    void shouldRejectConfigChangesWhenActiveComponentsHaveScores() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(schoolRepository.findActiveByIdForUpdate(SCHOOL_ID)).thenReturn(Mono.just(school()));
        when(caScoreRepository.existsBySchoolIdAndActiveComponents(SCHOOL_ID)).thenReturn(Mono.just(true));

        StepVerifier.create(resultService.configureCa(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CA_CONFIG_IN_USE");
                })
                .verify();

        verify(caComponentRepository, never()).deactivateActiveBySchoolId(SCHOOL_ID);
        verify(caComponentRepository, never()).insert(any(CaComponent.class));
    }

    @Test
    @DisplayName("Should reject invalid weights before auth lookup")
    void shouldRejectInvalidWeightsBeforeAuthLookup() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(new CaConfigRequest.CaComponentRequest("Test", 20, 20, 1)),
                50);

        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_WEIGHTS");
                })
                .verify();

        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Should reject duplicate CA component names before auth lookup")
    void shouldRejectDuplicateComponentNamesBeforeAuthLookup() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(
                        new CaConfigRequest.CaComponentRequest("Test", 20, 20, 1),
                        new CaConfigRequest.CaComponentRequest(" test ", 20, 20, 2)),
                60);

        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("DUPLICATE_CA_COMPONENT");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("components.name");
                })
                .verify();

        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Should require school context for CA configuration")
    void shouldRequireSchoolContextForCaConfiguration() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build()));

        StepVerifier.create(resultService.configureCa(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();

        verifyNoInteractions(schoolRepository);
    }

    @Test
    @DisplayName("Should get CA config and compute exam weight from active components")
    void shouldGetCaConfigAndComputeExamWeight() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(caComponentRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID))
                .thenReturn(Flux.just(
                        component("First Test", 10, 15, 1),
                        component("Second Test", 20, 25, 2)));

        StepVerifier.create(resultService.getCaConfig())
                .assertNext(response -> {
                    assertThat(response.componentCount()).isEqualTo(2);
                    assertThat(response.examWeightPercentage()).isEqualTo(60.0);
                    assertThat(response.message()).isEqualTo("Current CA configuration");
                })
                .verifyComplete();

        verify(caComponentRepository).findBySchoolIdAndIsActiveTrue(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should enter CA scores after validating context")
    void shouldEnterCaScoresAfterValidatingContext() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        mockScoreContext();
        when(caComponentRepository.findByIdAndSchoolIdAndIsActiveTrue(COMPONENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(component("First Test", 20, 20, 1)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());
        when(caScoreRepository.insert(any(CaScore.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(computationEngine.computeFinalScores(CLASS_ID, TERM_ID, SUBJECT_ID)).thenReturn(Mono.empty());
        when(computationEngine.computeSubjectPositions(CLASS_ID, TERM_ID)).thenReturn(Mono.empty());
        when(computationEngine.computeClassRankings(any(), any(), any(), any())).thenReturn(Mono.empty());

        CaScoreRequest request = validCaScoreRequest();

        StepVerifier.create(resultService.enterCaScores(request))
                .assertNext(response -> {
                    assertThat(response.scoresEntered()).isEqualTo(1);
                    assertThat(response.message()).isEqualTo("CA scores recorded");
                })
                .verifyComplete();

        ArgumentCaptor<CaScore> captor = ArgumentCaptor.forClass(CaScore.class);
        verify(caScoreRepository).insert(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(captor.getValue().getRecordedBy()).isEqualTo(USER_ID);
        verify(computationEngine).computeFinalScores(CLASS_ID, TERM_ID, SUBJECT_ID);
    }

    @Test
    @DisplayName("Should reject duplicate students in CA score request before auth lookup")
    void shouldRejectDuplicateStudentsInCaScoreRequestBeforeAuthLookup() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID,
                CLASS_ID,
                SUBJECT_ID,
                COMPONENT_ID,
                20,
                List.of(
                        new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.TEN),
                        new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.ONE)));

        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("DUPLICATE_SCORE_ENTRY");
                })
                .verify();

        verifyNoInteractions(jwtUtils);
    }

    @Test
    @DisplayName("Should enter exam scores and recompute final scores")
    void shouldEnterExamScoresAndRecomputeFinalScores() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        mockScoreContext();
        when(examRepository.findByIdAndSchoolIdAndTermId(EXAM_ID, SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(exam()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());
        when(scoreRepository.insert(any(ResultScore.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(computationEngine.computeFinalScores(CLASS_ID, TERM_ID, SUBJECT_ID)).thenReturn(Mono.empty());
        when(computationEngine.computeSubjectPositions(CLASS_ID, TERM_ID)).thenReturn(Mono.empty());
        when(computationEngine.computeClassRankings(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(resultService.enterExamScores(validExamScoreRequest()))
                .assertNext(response -> {
                    assertThat(response.scoresEntered()).isEqualTo(1);
                    assertThat(response.finalScoresComputed()).isEqualTo(1);
                })
                .verifyComplete();

        verify(scoreRepository).insert(any(ResultScore.class));
        verify(computationEngine).computeFinalScores(CLASS_ID, TERM_ID, SUBJECT_ID);
    }

    @Test
    @DisplayName("Should map duplicate exam score race to business error")
    void shouldMapDuplicateExamScoreRaceToBusinessError() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        mockScoreContext();
        when(examRepository.findByIdAndSchoolIdAndTermId(EXAM_ID, SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(exam()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());
        when(scoreRepository.insert(any(ResultScore.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate score")));

        StepVerifier.create(resultService.enterExamScores(validExamScoreRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCORE_ALREADY_EXISTS");
                })
                .verify();
    }

    @Test
    @DisplayName("Should update score with row lock, audit log and recompute")
    void shouldUpdateScoreWithRowLockAuditAndRecompute() {
        passThroughTransaction();
        UUID scoreId = UUID.randomUUID();
        ResultScore score = existingScore(scoreId, BigDecimal.valueOf(55));
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(scoreRepository.findByIdAndSchoolIdForUpdate(scoreId, SCHOOL_ID)).thenReturn(Mono.just(score));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());
        when(scoreRepository.save(any(ResultScore.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(auditLogRepository.insert(any(ScoreAuditLog.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(computationEngine.computeFinalScores(CLASS_ID, TERM_ID, SUBJECT_ID)).thenReturn(Mono.empty());
        when(computationEngine.computeSubjectPositions(CLASS_ID, TERM_ID)).thenReturn(Mono.empty());
        when(computationEngine.computeClassRankings(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(resultService.updateScore(scoreId, new UpdateScoreRequest(BigDecimal.valueOf(70), "Correction")))
                .assertNext(response -> {
                    assertThat(response.scoreId()).isEqualTo(scoreId);
                    assertThat(response.previousScore()).isEqualByComparingTo("55");
                    assertThat(response.newScore()).isEqualByComparingTo("70");
                })
                .verifyComplete();

        ArgumentCaptor<ScoreAuditLog> captor = ArgumentCaptor.forClass(ScoreAuditLog.class);
        verify(scoreRepository).findByIdAndSchoolIdForUpdate(scoreId, SCHOOL_ID);
        verify(auditLogRepository).insert(captor.capture());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(captor.getValue().getOldScore()).isEqualByComparingTo("55");
        assertThat(captor.getValue().getNewScore()).isEqualByComparingTo("70");
    }

    @Test
    @DisplayName("Should reject update score when score exceeds max score")
    void shouldRejectUpdateWhenScoreExceedsMaxScore() {
        passThroughTransaction();
        UUID scoreId = UUID.randomUUID();
        ResultScore score = existingScore(scoreId, BigDecimal.valueOf(55));
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(scoreRepository.findByIdAndSchoolIdForUpdate(scoreId, SCHOOL_ID)).thenReturn(Mono.just(score));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.updateScore(scoreId, new UpdateScoreRequest(BigDecimal.valueOf(101), "Reason")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                })
                .verify();

        verify(scoreRepository, never()).save(any(ResultScore.class));
        verify(auditLogRepository, never()).insert(any(ScoreAuditLog.class));
    }

    @Test
    @DisplayName("Should reject update score when results published")
    void shouldRejectUpdateWhenResultsPublished() {
        passThroughTransaction();
        UUID scoreId = UUID.randomUUID();
        ResultScore score = existingScore(scoreId, BigDecimal.valueOf(55));
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(scoreRepository.findByIdAndSchoolIdForUpdate(scoreId, SCHOOL_ID)).thenReturn(Mono.just(score));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().schoolId(SCHOOL_ID).termId(TERM_ID).build()));

        StepVerifier.create(resultService.updateScore(scoreId, new UpdateScoreRequest(BigDecimal.TEN, "Reason")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_ALREADY_PUBLISHED");
                })
                .verify();

        verify(scoreRepository, never()).save(any(ResultScore.class));
    }

    @Test
    @DisplayName("Should get student result for parent access when published")
    void shouldGetStudentResultForParentAccessWhenPublished() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        mockStudentResultGraph();
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(guardianId, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder()
                        .guardianId(guardianId)
                        .studentId(STUDENT_ID)
                        .canViewResults(true)
                        .build()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().schoolId(SCHOOL_ID).termId(TERM_ID).build()));
        when(studentTraitAssessmentRepository.findForStudentResult(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(
                        new StudentTraitAssessmentView("Punctuality", "AFFECTIVE", "A", "Always early"),
                        new StudentTraitAssessmentView("Handwriting", "PSYCHOMOTOR", "B", "Neat work")));

        StepVerifier.create(resultService.getStudentResult(STUDENT_ID, TERM_ID))
                .assertNext(result -> {
                    assertThat(result.student().studentId()).isEqualTo(STUDENT_ID);
                    assertThat(result.student().fullName()).isEqualTo("Test Student");
                    assertThat(result.term().name()).isEqualTo("First Term");
                    assertThat(result.subjects()).hasSize(1);
                    assertThat(result.summary().subjectsTaken()).isEqualTo(1);
                    assertThat(result.teacherComment()).isEqualTo("Doing well");
                    assertThat(result.behaviouralAssessments())
                            .extracting(StudentResultResponse.TraitAssessment::name)
                            .containsExactly("Punctuality");
                    assertThat(result.psychomotorAssessments())
                            .extracting(StudentResultResponse.TraitAssessment::name)
                            .containsExactly("Handwriting");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should compute a missing subject grade in the parent report")
    void shouldComputeMissingSubjectGradeInParentReport() {
        UUID guardianId = UUID.randomUUID();
        FinalScore scoreWithoutGrade = finalScore();
        scoreWithoutGrade.setFinalScore(BigDecimal.valueOf(55));
        scoreWithoutGrade.setGrade(null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        mockStudentResultGraph();
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(
                STUDENT_ID, TERM_ID, SCHOOL_ID)).thenReturn(Flux.just(scoreWithoutGrade));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(guardianId, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder()
                        .guardianId(guardianId)
                        .studentId(STUDENT_ID)
                        .canViewResults(true)
                        .build()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().schoolId(SCHOOL_ID).termId(TERM_ID).build()));

        StepVerifier.create(resultService.getStudentResult(STUDENT_ID, TERM_ID))
                .assertNext(result -> {
                    assertThat(result.subjects()).hasSize(1);
                    assertThat(result.subjects().getFirst().finalScore()).isEqualByComparingTo("55");
                    assertThat(result.subjects().getFirst().grade()).isEqualTo("C5");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject parent result access when link cannot view results")
    void shouldRejectParentResultAccessWhenLinkCannotViewResults() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(guardianId, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder()
                        .guardianId(guardianId)
                        .studentId(STUDENT_ID)
                        .canViewResults(false)
                        .build()));

        StepVerifier.create(resultService.getStudentResult(STUDENT_ID, TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject parent result access when not published")
    void shouldRejectGetStudentResultForParentAccessWhenNotPublished() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(guardianId, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder()
                        .guardianId(guardianId)
                        .studentId(STUDENT_ID)
                        .canViewResults(true)
                        .build()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.getStudentResult(STUDENT_ID, TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_NOT_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should get class result sheet with fully aggregated data")
    void shouldGetClassResultSheetWithFullyAggregatedData() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(ClassEntity.builder().id(CLASS_ID).name("Grade 1").build()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID))
                .thenReturn(Mono.just(1L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID))
                .thenReturn(Flux.just(student()));

        ClassSubject cs = ClassSubject.builder().classId(CLASS_ID).subjectId(SUBJECT_ID).isActive(true).build();
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID))
                .thenReturn(Flux.just(cs));
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Mono.just(Subject.builder().id(SUBJECT_ID).name("Mathematics").build()));

        ClassRanking rnk = ranking();
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(rnk));
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID))
                .thenReturn(Mono.empty());

        FinalScore finalScore = finalScore();
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(finalScore));

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.className()).isEqualTo("Grade 1");
                    assertThat(response.termName()).isEqualTo("First Term");
                    assertThat(response.classSize()).isEqualTo(1);
                    assertThat(response.subjects()).containsExactly("Mathematics");
                    assertThat(response.students()).hasSize(1);
                    ClassResultSheetResponse.StudentRow row = response.students().get(0);
                    assertThat(row.name()).isEqualTo("Test Student");
                    assertThat(row.position()).isEqualTo(1);
                    assertThat(row.average()).isEqualTo(78.0);
                    assertThat(row.overallGrade()).isEqualTo("B2");
                    assertThat(row.subjects()).hasSize(1);
                    assertThat(row.subjects().get(0).subject()).isEqualTo("Mathematics");
                    assertThat(row.subjects().get(0).finalScore()).isEqualTo(78.0);
                    assertThat(row.subjects().get(0).grade()).isEqualTo("B2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should derive missing class and subject grades from configured grading rules")
    void shouldDeriveMissingGradesFromConfiguredRules() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(ClassEntity.builder().id(CLASS_ID).name("Grade 1").build()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID))
                .thenReturn(Mono.just(1L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID))
                .thenReturn(Flux.just(student()));
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID))
                .thenReturn(Flux.just(ClassSubject.builder()
                        .classId(CLASS_ID)
                        .subjectId(SUBJECT_ID)
                        .isActive(true)
                        .build()));
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Mono.just(Subject.builder().id(SUBJECT_ID).name("Mathematics").build()));

        ClassRanking ranking = ranking();
        ranking.setAveragePercentage(BigDecimal.valueOf(65));
        ranking.setOverallGrade(null);
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(ranking));

        FinalScore finalScore = finalScore();
        finalScore.setFinalScore(BigDecimal.valueOf(65));
        finalScore.setGrade(null);
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(
                STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(finalScore));

        GradeConfig gradeConfig = GradeConfig.builder()
                .schoolId(SCHOOL_ID)
                .isActive(true)
                .config(new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                        {
                          "grades": [
                            {"grade": "DIST", "minScore": 70, "maxScore": 100},
                            {"grade": "MERIT", "minScore": 60, "maxScore": 69},
                            {"grade": "PASS", "minScore": 0, "maxScore": 59}
                          ]
                        }
                        """))
                .build();
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID))
                .thenReturn(Mono.just(gradeConfig));

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    ClassResultSheetResponse.StudentRow row = response.students().getFirst();
                    assertThat(row.overallGrade()).isEqualTo("MERIT");
                    assertThat(row.subjects().getFirst().grade()).isEqualTo("MERIT");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should publish results after validating term and school context")
    void shouldPublishResultsAfterValidatingTermContext() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());
        when(publishedResultRepository.save(any(PublishedResult.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(resultService.publishResults(TERM_ID))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(PublishResultResponse.class);
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.status()).isEqualTo("PUBLISHED");
                    assertThat(response.message()).contains("Parents can now view");
                })
                .verifyComplete();

        verify(publishedResultRepository).save(any(PublishedResult.class));
    }

    @Test
    @DisplayName("Should reject publishing when term is not found in school")
    void shouldRejectPublishingWhenTermNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.publishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();

        verify(publishedResultRepository, never()).save(any(PublishedResult.class));
    }

    @Test
    @DisplayName("Should reject publishing when results already published")
    void shouldRejectPublishingWhenAlreadyPublished() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().schoolId(SCHOOL_ID).termId(TERM_ID).build()));

        StepVerifier.create(resultService.publishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_ALREADY_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should map duplicate publish race to already-published error")
    void shouldMapDuplicatePublishRaceToAlreadyPublishedError() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());
        when(publishedResultRepository.save(any(PublishedResult.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate")));

        StepVerifier.create(resultService.publishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_ALREADY_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should unpublish results when currently published")
    void shouldUnpublishResultsWhenCurrentlyPublished() {
        passThroughTransaction();
        PublishedResult published = PublishedResult.builder()
                .schoolId(SCHOOL_ID)
                .termId(TERM_ID)
                .build();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.just(published));
        when(publishedResultRepository.delete(published)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .assertNext(response -> {
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.status()).isEqualTo("UNPUBLISHED");
                })
                .verifyComplete();

        verify(publishedResultRepository).delete(published);
    }

    @Test
    @DisplayName("Should reject unpublish when results are not published")
    void shouldRejectUnpublishWhenNotPublished() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_NOT_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject unpublish when term is not found in school")
    void shouldRejectUnpublishWhenTermNotFoundInSchool() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();

        verify(publishedResultRepository, never()).findBySchoolIdAndTermId(any(), any());
        verify(publishedResultRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should reject unpublish when user has no school context")
    void shouldRejectUnpublishWhenUserHasNoSchoolContext() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build()));

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();

        verifyNoInteractions(termRepository);
        verifyNoInteractions(publishedResultRepository);
    }

    @Test
    @DisplayName("Should verify delete is called on the correct PublishedResult entity")
    void shouldVerifyDeleteCalledOnCorrectPublishedResultEntity() {
        passThroughTransaction();
        UUID publishedId = UUID.randomUUID();
        PublishedResult published = PublishedResult.builder()
                .id(publishedId)
                .schoolId(SCHOOL_ID)
                .termId(TERM_ID)
                .publishedBy(USER_ID)
                .publishedAt(Instant.parse("2026-06-06T10:00:00Z"))
                .build();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.just(published));
        when(publishedResultRepository.delete(any(PublishedResult.class))).thenReturn(Mono.empty());

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("UNPUBLISHED");
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                })
                .verifyComplete();

        ArgumentCaptor<PublishedResult> captor = ArgumentCaptor.forClass(PublishedResult.class);
        verify(publishedResultRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(publishedId);
        assertThat(captor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(captor.getValue().getTermId()).isEqualTo(TERM_ID);
    }

    @Test
    @DisplayName("Should return current grading rules when config exists")
    void shouldReturnCurrentGradingRulesWhenConfigExists() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        com.fasterxml.jackson.databind.JsonNode configJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("[{\"grade\":\"A\"},{\"grade\":\"B\"},{\"grade\":\"C\"}]");
        GradeConfig gradeConfig = GradeConfig.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .config(configJson)
                .isActive(true)
                .build();
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(gradeConfig));

        StepVerifier.create(resultService.getGradingRules())
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isEqualTo(3);
                    assertThat(response.message()).isEqualTo("Current grading rules");
                })
                .verifyComplete();

        verify(gradeConfigRepository).findBySchoolId(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should return default response when no grading rules configured")
    void shouldReturnDefaultResponseWhenNoGradingRulesConfigured() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.getGradingRules())
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isZero();
                    assertThat(response.message()).isEqualTo("No grading rules configured");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject get grading rules when user has no school context")
    void shouldRejectGetGradingRulesWhenUserHasNoSchoolContext() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build()));

        StepVerifier.create(resultService.getGradingRules())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();

        verifyNoInteractions(gradeConfigRepository);
    }

    @Test
    @DisplayName("Should handle null config in GradeConfig gracefully")
    void shouldHandleNullConfigInGradeConfigGracefully() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        GradeConfig gradeConfig = GradeConfig.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .config(null)
                .isActive(true)
                .build();
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(gradeConfig));

        StepVerifier.create(resultService.getGradingRules())
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isZero();
                    assertThat(response.message()).isEqualTo("Current grading rules");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should configure grading rules when no previous config exists")
    void shouldConfigureGradingRulesWhenNoPreviousConfigExists() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        com.fasterxml.jackson.databind.JsonNode configJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("[{\"grade\":\"A\",\"min\":70,\"max\":100},{\"grade\":\"B\",\"min\":60,\"max\":69}]");
        GradingRuleRequest request = new GradingRuleRequest(configJson);
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.empty());
        when(gradeConfigRepository.save(any(GradeConfig.class)))
                .thenAnswer(invocation -> {
                    GradeConfig saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return Mono.just(saved);
                });

        StepVerifier.create(resultService.configureGradingRules(request))
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isEqualTo(2);
                    assertThat(response.message()).isEqualTo("Grading rules updated");
                })
                .verifyComplete();

        ArgumentCaptor<GradeConfig> captor = ArgumentCaptor.forClass(GradeConfig.class);
        verify(gradeConfigRepository).save(captor.capture());
        GradeConfig saved = captor.getValue();
        assertThat(saved.getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(saved.getConfig()).isEqualTo(configJson);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update grading rules when config already exists")
    void shouldUpdateGradingRulesWhenConfigAlreadyExists() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        com.fasterxml.jackson.databind.JsonNode newConfigJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("[{\"grade\":\"A\",\"min\":80,\"max\":100}]");
        GradingRuleRequest request = new GradingRuleRequest(newConfigJson);

        UUID existingId = UUID.randomUUID();
        GradeConfig existing = GradeConfig.builder()
                .id(existingId)
                .schoolId(SCHOOL_ID)
                .config(new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree("[{\"grade\":\"A\"},{\"grade\":\"B\"}]"))
                .isActive(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(existing));
        when(gradeConfigRepository.save(any(GradeConfig.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(resultService.configureGradingRules(request))
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isEqualTo(1);
                    assertThat(response.message()).isEqualTo("Grading rules updated");
                })
                .verifyComplete();

        ArgumentCaptor<GradeConfig> captor = ArgumentCaptor.forClass(GradeConfig.class);
        verify(gradeConfigRepository).save(captor.capture());
        GradeConfig saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(existingId);
        assertThat(saved.getConfig()).isEqualTo(newConfigJson);
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should configure independent grading schemes by education level")
    void shouldConfigureIndependentGradingSchemesByEducationLevel() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        com.fasterxml.jackson.databind.JsonNode configJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("""
                        {
                          "byEducationLevel": {
                            "NURSERY": {
                              "passMark": 50,
                              "grades": [
                                {"grade": "Excellent", "minScore": 80, "maxScore": 100, "remark": "Excellent"},
                                {"grade": "Developing", "minScore": 0, "maxScore": 79, "remark": "Developing"}
                              ]
                            },
                            "PRIMARY": {
                              "passMark": 40,
                              "grades": [
                                {"grade": "A", "minScore": 70, "maxScore": 100, "remark": "Excellent"},
                                {"grade": "C", "minScore": 40, "maxScore": 69, "remark": "Pass"},
                                {"grade": "F", "minScore": 0, "maxScore": 39, "remark": "Fail"}
                              ]
                            }
                          }
                        }
                        """);
        GradingRuleRequest request = new GradingRuleRequest(configJson);
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.empty());
        when(gradeConfigRepository.save(any(GradeConfig.class)))
                .thenAnswer(invocation -> {
                    GradeConfig saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return Mono.just(saved);
                });

        StepVerifier.create(resultService.configureGradingRules(request))
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isEqualTo(5);
                    assertThat(response.config().path("byEducationLevel").path("NURSERY").path("passMark").asInt()).isEqualTo(50);
                    assertThat(response.config().path("byEducationLevel").path("PRIMARY").path("grades")).hasSize(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject configure grading rules when user has no school context")
    void shouldRejectConfigureGradingRulesWhenUserHasNoSchoolContext() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build()));
        GradingRuleRequest request = new GradingRuleRequest(
                new com.fasterxml.jackson.databind.ObjectMapper().readTree("[{\"grade\":\"A\"}]"));

        StepVerifier.create(resultService.configureGradingRules(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();

        verifyNoInteractions(gradeConfigRepository);
    }

    @Test
    @DisplayName("Should configure grading rules with empty config array")
    void shouldConfigureGradingRulesWithEmptyConfigArray() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        com.fasterxml.jackson.databind.JsonNode emptyConfig = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("[]");
        GradingRuleRequest request = new GradingRuleRequest(emptyConfig);
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.empty());
        when(gradeConfigRepository.save(any(GradeConfig.class)))
                .thenAnswer(invocation -> {
                    GradeConfig saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return Mono.just(saved);
                });

        StepVerifier.create(resultService.configureGradingRules(request))
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isZero();
                    assertThat(response.message()).isEqualTo("Grading rules updated");
                })
                .verifyComplete();

        verify(gradeConfigRepository).save(any(GradeConfig.class));
    }

    @Test
    @DisplayName("Should use saved entity config for response, not request config")
    void shouldUseSavedEntityConfigForResponseNotRequestConfig() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode requestConfig = mapper.readTree("[{\"grade\":\"A\"},{\"grade\":\"B\"}]");
        GradingRuleRequest request = new GradingRuleRequest(requestConfig);

        // Simulate repo returning saved entity with potentially different config
        GradeConfig savedEntity = GradeConfig.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .config(mapper.readTree("[{\"grade\":\"A\"},{\"grade\":\"B\"},{\"grade\":\"C\"}]"))
                .isActive(true)
                .build();
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.empty());
        when(gradeConfigRepository.save(any(GradeConfig.class))).thenReturn(Mono.just(savedEntity));

        StepVerifier.create(resultService.configureGradingRules(request))
                .assertNext(response -> {
                    // Response should use saved.getConfig().size() (3), not request.config().size() (2)
                    assertThat(response.gradesCount()).isEqualTo(3);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should recompute rankings and return ranked student count")
    void shouldRecomputeRankingsAndReturnRankedStudentCount() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(computationEngine.computeSubjectPositions(CLASS_ID, TERM_ID)).thenReturn(Mono.empty());
        when(computationEngine.computeClassRankings(any(), any(), any(), any())).thenReturn(Mono.empty());
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(
                        ranking(),
                        ClassRanking.builder()
                                .id(UUID.randomUUID())
                                .schoolId(SCHOOL_ID)
                                .studentId(UUID.randomUUID())
                                .classId(CLASS_ID)
                                .termId(TERM_ID)
                                .classPosition(2)
                                .build()));

        StepVerifier.create(resultService.recomputeRankings(CLASS_ID, TERM_ID, null))
                .expectNext(2)
                .verifyComplete();

        verify(computationEngine).computeClassRankings(CLASS_ID, TERM_ID, SCHOOL_ID, new ScoreComputationEngine.RankingParameters(40, 100));
    }

    @Test
    @DisplayName("Should reject recompute rankings when class id is missing")
    void shouldRejectRecomputeRankingsWhenClassIdMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));

        StepVerifier.create(resultService.recomputeRankings(null, TERM_ID, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_RANKING_REQUEST");
                })
                .verify();

        verify(classRepository, never()).findByIdAndSchoolId(any(), any());
    }

    @Test
    @DisplayName("Should get my children results without duplicates and only viewable links")
    void shouldGetMyChildrenResultsWithoutDuplicatesAndOnlyViewableLinks() {
        UUID guardianId = UUID.randomUUID();
        UUID anotherStudentId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(
                        StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build(),
                        StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build(),
                        StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(anotherStudentId).canViewResults(false).build(),
                        StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(anotherStudentId).canViewResults(true).build()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).hasSize(2);
                    assertThat(results).extracting(MyChildResultResponse::studentId)
                            .containsExactlyInAnyOrder(STUDENT_ID, anotherStudentId);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get my children results with fully populated data including termId, summary, and top subjects")
    void shouldGetMyChildrenResultsWithFullyPopulatedData() {
        UUID guardianId = UUID.randomUUID();
        Student child = student(); // STUDENT_ID, CLASS_ID
        Term activeTerm = term(); // TERM_ID
        ClassEntity childClass = ClassEntity.builder().id(CLASS_ID).name("Grade 1").build();
        FinalScore score = finalScore(); // STUDENT_ID, TERM_ID, SUBJECT_ID, score 78
        ClassRanking rnk = ranking(); // STUDENT_ID, TERM_ID, CLASS_ID, overallGrade B2
        Subject math = Subject.builder().id(SUBJECT_ID).name("Mathematics").build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build()));

        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(child));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(childClass));
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(activeTerm));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder()
                        .schoolId(SCHOOL_ID)
                        .termId(TERM_ID)
                        .build()));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(score));
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(rnk));
        ReportComment commentWithAttendance = ReportComment.builder()
                .studentId(STUDENT_ID)
                .termId(TERM_ID)
                .schoolId(SCHOOL_ID)
                .attendanceDaysOpen(40)
                .attendanceDaysPresent(38)
                .build();
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(commentWithAttendance));
        when(subjectRepository.findById(SUBJECT_ID))
                .thenReturn(Mono.just(math));

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    MyChildResultResponse res = results.get(0);
                    assertThat(res.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(res.termId()).isEqualTo(TERM_ID);
                    assertThat(res.firstName()).isEqualTo("Test");
                    assertThat(res.lastName()).isEqualTo("Student");
                    assertThat(res.className()).isEqualTo("Grade 1");
                    assertThat(res.termName()).isEqualTo("First Term");
                    assertThat(res.summary()).isNotNull();
                    assertThat(res.summary().average()).isEqualTo(78.0);
                    assertThat(res.summary().totalSubjects()).isEqualTo(1);
                    assertThat(res.summary().grade()).isEqualTo("B2");
                    assertThat(res.topSubjects()).hasSize(1);
                    assertThat(res.topSubjects().get(0).name()).isEqualTo("Mathematics");
                    assertThat(res.topSubjects().get(0).score()).isEqualTo(78.0);
                    assertThat(res.topSubjects().get(0).grade()).isEqualTo("B2");
                    assertThat(res.attendance()).isNotNull();
                    assertThat(res.attendance().daysOpen()).isEqualTo(40);
                    assertThat(res.attendance().daysPresent()).isEqualTo(38);
                    assertThat(res.attendance().daysAbsent()).isEqualTo(2);
                    assertThat(res.attendance().attendanceRate()).isEqualTo(95.0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should add teacher comment after validating student and term context")
    void shouldAddTeacherCommentAfterValidatingContext() {
        ReportComment existing = comment();
        existing.setUpdatedAt(Instant.now().minusSeconds(60));
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existing));
        when(commentRepository.save(any(ReportComment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "Excellent progress"))
                .assertNext(response -> {
                    assertThat(response).isInstanceOf(ReportCommentResponse.class);
                    assertThat(response.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.comment()).isEqualTo("Excellent progress");
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(commentRepository).save(any(ReportComment.class));
    }

    @Test
    @DisplayName("Should create principal comment when no record exists")
    void shouldCreatePrincipalCommentWhenNoRecordExists() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(commentRepository.save(any(ReportComment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, "Keep aiming higher"))
                .assertNext(response -> {
                    assertThat(response.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.termId()).isEqualTo(TERM_ID);
                    assertThat(response.comment()).isEqualTo("Keep aiming higher");
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(commentRepository).save(any(ReportComment.class));
    }

    @Test
    @DisplayName("Should auto-generate teacher comment from student grading")
    void shouldAutoGenerateTeacherCommentFromStudentGrading() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(finalScore()));
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(ranking()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(commentRepository.save(any(ReportComment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "", true))
                .assertNext(response -> {
                    assertThat(response.comment()).contains("Test");
                    assertThat(response.comment()).contains("78.0%");
                    assertThat(response.comment()).contains("B2");
                    assertThat(response.comment()).contains("excellent performance");
                })
                .verifyComplete();

        ArgumentCaptor<ReportComment> captor = ArgumentCaptor.forClass(ReportComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getTeacherCommentAuto()).contains("78.0%");
        assertThat(captor.getValue().getTeacherCommentFinal()).isEqualTo(captor.getValue().getTeacherComment());
        assertThat(captor.getValue().getCommentsGeneratedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should auto-generate principal comment when request comment is AUTO")
    void shouldAutoGeneratePrincipalCommentWhenRequestCommentIsAuto() {
        ReportComment existing = comment();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(finalScore()));
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existing));
        when(commentRepository.save(any(ReportComment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, "AUTO"))
                .assertNext(response -> {
                    assertThat(response.comment()).contains("78.0%");
                    assertThat(response.comment()).contains("A1");
                    assertThat(response.comment()).contains("excellent standard");
                })
                .verifyComplete();

        assertThat(existing.getPrincipalCommentAuto()).contains("78.0%");
        assertThat(existing.getPrincipalCommentFinal()).isEqualTo(existing.getPrincipalComment());
        assertThat(existing.getCommentsGeneratedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject teacher comment when student does not exist in school")
    void shouldRejectTeacherCommentWhenStudentNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "Any comment"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");
                })
                .verify();

        verify(commentRepository, never()).save(any(ReportComment.class));
    }

    @Test
    @DisplayName("Should recover from duplicate insert race when adding teacher comment")
    void shouldRecoverFromDuplicateInsertRaceWhenAddingTeacherComment() {
        ReportComment existingAfterRace = comment();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty(), Mono.just(existingAfterRace));
        when(commentRepository.save(any(ReportComment.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate")), Mono.just(existingAfterRace));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "Steady growth"))
                .assertNext(response -> {
                    assertThat(response.comment()).isEqualTo("Steady growth");
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();

        verify(commentRepository, times(2)).save(any(ReportComment.class));
    }

    @Test
    @DisplayName("Should generate report cards with deduplicated students and retrieve completed job")
    void shouldGenerateReportCardsAndFetchJobStatus() {
        ReportCardRequest request = new ReportCardRequest(
                TERM_ID,
                CLASS_ID,
                null,
                List.of(STUDENT_ID, STUDENT_ID),
                true,
                true,
                true,
                "Well done",
                "PDF");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));

        ReportCardJobResponse generated = resultService.generateReportCards(request).block();

        assertThat(generated).isNotNull();
        assertThat(generated.status()).isEqualTo("PROCESSING");
        assertThat(generated.totalStudents()).isEqualTo(1);
        assertThat(generated.completedStudents()).isZero();

        StepVerifier.create(resultService.getReportCardJobStatus(generated.jobId()))
                .assertNext(job -> {
                    assertThat(job.status()).isEqualTo("COMPLETED");
                    assertThat(job.totalStudents()).isEqualTo(1);
                    assertThat(job.completedStudents()).isEqualTo(1);
                    assertThat(job.failedStudents()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject report card generation when no students are provided")
    void shouldRejectReportCardGenerationWhenStudentIdsAreEmpty() {
        ReportCardRequest request = new ReportCardRequest(
                TERM_ID,
                CLASS_ID,
                null,
                List.of(),
                false,
                false,
                false,
                null,
                "PDF");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));

        StepVerifier.create(resultService.generateReportCards(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_REPORT_CARD_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject report card generation when template is missing")
    void shouldRejectReportCardGenerationWhenTemplateIsMissing() {
        ReportCardRequest request = new ReportCardRequest(
                TERM_ID,
                CLASS_ID,
                TEMPLATE_ID,
                List.of(STUDENT_ID),
                true,
                true,
                true,
                null,
                "PDF");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(reportCardTemplateRepository.findActiveByIdAndSchoolId(TEMPLATE_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.generateReportCards(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("REPORT_CARD_TEMPLATE_NOT_FOUND");
                })
                .verify();

        verify(studentRepository, never()).findByIdAndSchoolIdAndDeletedAtIsNull(any(UUID.class), any(UUID.class));
    }

    @Test
    @DisplayName("Should reject fetching report card job when job does not exist")
    void shouldRejectFetchingReportCardJobWhenNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));

        StepVerifier.create(resultService.getReportCardJobStatus(UUID.randomUUID()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("REPORT_CARD_JOB_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should create report card template and clear existing default")
    void shouldCreateReportCardTemplateAndClearExistingDefault() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(reportCardTemplateRepository.existsActiveByNormalizedName(SCHOOL_ID, "PRIMARY", "Standard Primary"))
                .thenReturn(Mono.just(false));
        when(reportCardTemplateRepository.clearDefaultForLevel(eq(SCHOOL_ID), eq("PRIMARY"), any(Instant.class)))
                .thenReturn(Mono.just(1));
        when(reportCardTemplateRepository.insert(
                eq(SCHOOL_ID),
                eq("Standard Primary"),
                eq("PRIMARY"),
                any(),
                eq(true),
                any(Instant.class),
                any(Instant.class)))
                .thenReturn(Mono.just(reportCardTemplate("Standard Primary", "PRIMARY", true)));

        ReportCardTemplateRequest request = new ReportCardTemplateRequest(
                " Standard Primary ",
                "primary_1",
                OBJECT_MAPPER.createObjectNode().put("layout", "standard"),
                true);

        StepVerifier.create(resultService.createReportCardTemplate(request))
                .assertNext(response -> {
                    assertThat(response.templateId()).isEqualTo(TEMPLATE_ID);
                    assertThat(response.name()).isEqualTo("Standard Primary");
                    assertThat(response.educationLevel()).isEqualTo("PRIMARY");
                    assertThat(response.isDefault()).isTrue();
                    assertThat(response.isActive()).isTrue();
                })
                .verifyComplete();

        verify(reportCardTemplateRepository).clearDefaultForLevel(eq(SCHOOL_ID), eq("PRIMARY"), any(Instant.class));
    }

    @Test
    @DisplayName("Should reject duplicate report card template names")
    void shouldRejectDuplicateReportCardTemplateNames() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(reportCardTemplateRepository.existsActiveByNormalizedName(SCHOOL_ID, "PRIMARY", "Standard Primary"))
                .thenReturn(Mono.just(true));

        ReportCardTemplateRequest request = new ReportCardTemplateRequest(
                "Standard Primary",
                "PRIMARY",
                OBJECT_MAPPER.createObjectNode().put("layout", "standard"),
                false);

        StepVerifier.create(resultService.createReportCardTemplate(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("REPORT_CARD_TEMPLATE_EXISTS");
                })
                .verify();

        verify(reportCardTemplateRepository, never()).insert(
                any(),
                anyString(),
                anyString(),
                any(),
                any(Boolean.class),
                any(Instant.class),
                any(Instant.class));
    }

    @Test
    @DisplayName("Should list active report card templates")
    void shouldListActiveReportCardTemplates() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(reportCardTemplateRepository.findActiveBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(reportCardTemplate("Standard Primary", "PRIMARY", true)));

        StepVerifier.create(resultService.getReportCardTemplates())
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.get(0).name()).isEqualTo("Standard Primary");
                    assertThat(response.get(0).educationLevel()).isEqualTo("PRIMARY");
                })
                .verifyComplete();

        verify(reportCardTemplateRepository).findActiveBySchoolId(SCHOOL_ID);
    }

    private ReportCardTemplate reportCardTemplate(String name, String educationLevel, boolean isDefault) {
        ReportCardTemplate template = new ReportCardTemplate();
        template.setId(TEMPLATE_ID);
        template.setSchoolId(SCHOOL_ID);
        template.setName(name);
        template.setEducationLevel(educationLevel);
        template.setConfig(OBJECT_MAPPER.createObjectNode().put("layout", "standard"));
        template.setDefaultTemplate(isDefault);
        template.setActive(true);
        template.setCreatedAt(Instant.parse("2026-06-18T10:00:00Z"));
        template.setUpdatedAt(Instant.parse("2026-06-18T10:00:00Z"));
        template.setVersion(0);
        return template;
    }

    private void passThroughTransaction() {
        when(transactionalOperator.transactional(org.mockito.ArgumentMatchers.<Mono<?>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CaConfigRequest validRequest() {
        return new CaConfigRequest(
                List.of(
                        new CaConfigRequest.CaComponentRequest("First Test", 20, 20, 1),
                        new CaConfigRequest.CaComponentRequest("Second Test", 20, 20, 2)),
                60);
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }

    private SchoolFeeUser parentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .roles(Set.of("PARENT"))
                .build();
    }

    private void mockScoreContext() {
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(subject()));
        when(classSubjectRepository.findByClassIdAndSubjectIdAndSchoolIdAndIsActiveTrue(
                CLASS_ID, SUBJECT_ID, SCHOOL_ID)).thenReturn(Mono.just(classSubject()));
    }

    private void mockStudentResultGraph() {
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(1L));
        when(academicSessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID))
                .thenReturn(Mono.just(session()));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(
                STUDENT_ID, TERM_ID, SCHOOL_ID)).thenReturn(Flux.just(finalScore()));
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(ranking()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(comment()));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Mono.just(subject()));
        when(caScoreRepository.findByStudentIdAndSubjectIdAndTermIdAndSchoolId(
                STUDENT_ID, SUBJECT_ID, TERM_ID, SCHOOL_ID)).thenReturn(Flux.just(caScore()));
        when(caComponentRepository.findById(COMPONENT_ID)).thenReturn(Mono.just(component("First Test", 20, 20, 1)));
    }

    private CaScoreRequest validCaScoreRequest() {
        return new CaScoreRequest(
                TERM_ID,
                CLASS_ID,
                SUBJECT_ID,
                COMPONENT_ID,
                20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.valueOf(15))));
    }

    private ExamScoreRequest validExamScoreRequest() {
        return new ExamScoreRequest(
                EXAM_ID,
                CLASS_ID,
                SUBJECT_ID,
                TERM_ID,
                100,
                List.of(new ExamScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.valueOf(75))));
    }

    private School school() {
        return School.builder()
                .id(SCHOOL_ID)
                .name("Grace International School")
                .isActive(true)
                .build();
    }

    private ClassEntity classEntity() {
        return ClassEntity.builder()
                .id(CLASS_ID)
                .schoolId(SCHOOL_ID)
                .name("Basic 1A")
                .academicSessionId(SESSION_ID)
                .isActive(true)
                .build();
    }

    private Term term() {
        return Term.builder()
                .id(TERM_ID)
                .sessionId(SESSION_ID)
                .name("First Term")
                .termNumber((short) 1)
                .startDate(LocalDate.parse("2026-01-10"))
                .endDate(LocalDate.parse("2026-04-10"))
                .isCurrent(true)
                .status("ACTIVE")
                .build();
    }

    private AcademicSession session() {
        return AcademicSession.builder()
                .id(SESSION_ID)
                .schoolId(SCHOOL_ID)
                .name("2025/2026")
                .build();
    }

    private Subject subject() {
        return Subject.builder()
                .id(SUBJECT_ID)
                .schoolId(SCHOOL_ID)
                .name("Mathematics")
                .isActive(true)
                .build();
    }

    private ClassSubject classSubject() {
        return ClassSubject.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .classId(CLASS_ID)
                .subjectId(SUBJECT_ID)
                .isActive(true)
                .build();
    }

    private Exam exam() {
        return Exam.builder()
                .id(EXAM_ID)
                .schoolId(SCHOOL_ID)
                .termId(TERM_ID)
                .name("End of Term")
                .examType("END_OF_TERM")
                .maxScore(100)
                .build();
    }

    private Student student() {
        return Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .admissionNumber("GIS-001")
                .firstName("Test")
                .lastName("Student")
                .currentClassId(CLASS_ID)
                .enrollmentStatus("ACTIVE")
                .build();
    }

    private ResultScore existingScore(UUID scoreId, BigDecimal score) {
        return ResultScore.builder()
                .id(scoreId)
                .schoolId(SCHOOL_ID)
                .examId(EXAM_ID)
                .studentId(STUDENT_ID)
                .subjectId(SUBJECT_ID)
                .classId(CLASS_ID)
                .termId(TERM_ID)
                .score(score)
                .maxScore(100)
                .recordedBy(USER_ID)
                .build();
    }

    private FinalScore finalScore() {
        return FinalScore.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .studentId(STUDENT_ID)
                .subjectId(SUBJECT_ID)
                .classId(CLASS_ID)
                .termId(TERM_ID)
                .caTotal(BigDecimal.valueOf(35))
                .caMaxTotal(40)
                .examScore(BigDecimal.valueOf(70))
                .examMaxScore(100)
                .finalScore(BigDecimal.valueOf(78))
                .grade("B2")
                .remark("Very Good")
                .points(BigDecimal.valueOf(3.5))
                .subjectPosition(1)
                .build();
    }

    private ClassRanking ranking() {
        return ClassRanking.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .studentId(STUDENT_ID)
                .classId(CLASS_ID)
                .termId(TERM_ID)
                .totalScore(BigDecimal.valueOf(78))
                .totalMaxScore(100)
                .averagePercentage(BigDecimal.valueOf(78))
                .overallGrade("B2")
                .classPosition(1)
                .outOf(1)
                .subjectsTaken(1)
                .subjectsPassed(1)
                .build();
    }

    private ReportComment comment() {
        return ReportComment.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .studentId(STUDENT_ID)
                .termId(TERM_ID)
                .teacherComment("Doing well")
                .principalComment("Keep it up")
                .attendanceDaysOpen(60)
                .attendanceDaysPresent(58)
                .attendanceDaysAbsent(2)
                .build();
    }

    private CaScore caScore() {
        return CaScore.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .studentId(STUDENT_ID)
                .subjectId(SUBJECT_ID)
                .classId(CLASS_ID)
                .termId(TERM_ID)
                .caComponentId(COMPONENT_ID)
                .score(BigDecimal.valueOf(18))
                .maxScore(20)
                .build();
    }

    private CaComponent component(String name, int maxScore, double weight, int sortOrder) {
        return CaComponent.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .name(name)
                .maxScore(maxScore)
                .weightPercentage(BigDecimal.valueOf(weight))
                .sortOrder(sortOrder)
                .isActive(true)
                .createdAt(Instant.parse("2026-06-06T10:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("Should lookup subjects for class successfully")
    void shouldLookupSubjectsForClassSuccessfully() {
        UUID classId = UUID.randomUUID();
        ClassSubject classSubject = ClassSubject.builder()
                .classId(classId)
                .subjectId(SUBJECT_ID)
                .schoolId(SCHOOL_ID)
                .isActive(true)
                .build();
        Subject subject = Subject.builder()
                .id(SUBJECT_ID)
                .schoolId(SCHOOL_ID)
                .name("Mathematics")
                .code("MTH101")
                .isActive(true)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(classId, SCHOOL_ID))
                .thenReturn(Mono.just(ClassEntity.builder().id(classId).schoolId(SCHOOL_ID).build()));
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(classId))
                .thenReturn(Flux.just(classSubject));
        when(subjectRepository.findByIdAndSchoolIdAndIsActiveTrue(SUBJECT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(subject));

        StepVerifier.create(resultService.getSubjectsForClass(classId))
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    SubjectLookupResponse res = results.get(0);
                    assertThat(res.id()).isEqualTo(SUBJECT_ID);
                    assertThat(res.name()).isEqualTo("Mathematics");
                    assertThat(res.code()).isEqualTo("MTH101");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should lookup CA components successfully")
    void shouldLookupCaComponentsSuccessfully() {
        CaComponent caComponent = CaComponent.builder()
                .id(COMPONENT_ID)
                .schoolId(SCHOOL_ID)
                .name("First CA")
                .maxScore(20)
                .isActive(true)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(caComponentRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID))
                .thenReturn(Flux.just(caComponent));

        StepVerifier.create(resultService.getCaComponents())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    CaComponentLookupResponse res = results.get(0);
                    assertThat(res.id()).isEqualTo(COMPONENT_ID);
                    assertThat(res.name()).isEqualTo("First CA");
                    assertThat(res.maxScore()).isEqualTo(20);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should lookup exams for term successfully")
    void shouldLookupExamsForTermSuccessfully() {
        Exam exam = Exam.builder()
                .id(EXAM_ID)
                .schoolId(SCHOOL_ID)
                .termId(TERM_ID)
                .name("Mid Term Exam")
                .maxScore(40)
                .weightPercentage(BigDecimal.valueOf(40))
                .build();

        CaComponent caComponent = CaComponent.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .name("CA")
                .weightPercentage(BigDecimal.valueOf(60))
                .isActive(true)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(term()));
        when(examRepository.findByTermId(TERM_ID))
                .thenReturn(Flux.just(exam));
        when(caComponentRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID))
                .thenReturn(Flux.just(caComponent));

        StepVerifier.create(resultService.getExamsForTerm(TERM_ID))
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    ExamLookupResponse res = results.get(0);
                    assertThat(res.id()).isEqualTo(EXAM_ID);
                    assertThat(res.name()).isEqualTo("Mid Term Exam");
                    assertThat(res.maxScore()).isEqualTo(40);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should auto-create default exam when none exist")
    void shouldAutoCreateDefaultExamWhenNoneExist() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(term()));
        when(examRepository.findByTermId(TERM_ID))
                .thenReturn(Flux.empty());
        when(caComponentRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID))
                .thenReturn(Flux.empty());

        // Mock user lookup
        User dbUser = User.builder()
                .id(UUID.randomUUID())
                .keycloakId(USER_ID)
                .build();
        when(userRepository.findByKeycloakIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Mono.just(dbUser));

        Exam savedExam = Exam.builder()
                .id(EXAM_ID)
                .schoolId(SCHOOL_ID)
                .termId(TERM_ID)
                .name("End of Term Exam")
                .maxScore(100)
                .build();
        when(examRepository.save(any(Exam.class)))
                .thenReturn(Mono.just(savedExam));

        StepVerifier.create(resultService.getExamsForTerm(TERM_ID))
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    ExamLookupResponse res = results.get(0);
                    assertThat(res.name()).isEqualTo("End of Term Exam");
                    assertThat(res.maxScore()).isEqualTo(100);
                })
                .verifyComplete();
    }

    // ========================================================================
    // ADDITIONAL CA CONFIG VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject null CaConfigRequest")
    void shouldRejectNullCaConfigRequest() {
        StepVerifier.create(resultService.configureCa(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null components list in CaConfigRequest")
    void shouldRejectNullComponentsInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(null, 60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject empty components list in CaConfigRequest")
    void shouldRejectEmptyComponentsInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(List.of(), 60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null component in CaConfigRequest")
    void shouldRejectNullComponentInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(Collections.singletonList(null), 60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null component name in CaConfigRequest")
    void shouldRejectNullComponentNameInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(new CaConfigRequest.CaComponentRequest(null, 20, 20, 1)),
                60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("components.name");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject non-positive max score in CaConfigRequest")
    void shouldRejectNonPositiveMaxScoreInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(new CaConfigRequest.CaComponentRequest("Test", 0, 20, 1)),
                60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("components.maxScore");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject negative sort order in CaConfigRequest")
    void shouldRejectNegativeSortOrderInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(new CaConfigRequest.CaComponentRequest("Test", 20, 20, -1)),
                60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("components.sortOrder");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject duplicate sort orders in CaConfigRequest")
    void shouldRejectDuplicateSortOrdersInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(
                        new CaConfigRequest.CaComponentRequest("Test 1", 20, 20, 1),
                        new CaConfigRequest.CaComponentRequest("Test 2", 20, 20, 1)),
                60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("DUPLICATE_CA_COMPONENT");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("components.sortOrder");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject non-finite component weight in CaConfigRequest")
    void shouldRejectNonFiniteComponentWeightInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(new CaConfigRequest.CaComponentRequest("Test", 20, Double.NaN, 1)),
                60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("components.weightPercentage");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject non-positive component weight in CaConfigRequest")
    void shouldRejectNonPositiveComponentWeightInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(new CaConfigRequest.CaComponentRequest("Test", 20, 0.0, 1)),
                60);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("components.weightPercentage");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject infinite exam weight in CaConfigRequest")
    void shouldRejectInfiniteExamWeightInCaConfigRequest() {
        CaConfigRequest request = new CaConfigRequest(
                List.of(new CaConfigRequest.CaComponentRequest("Test", 20, 40.0, 1)),
                Double.POSITIVE_INFINITY);
        StepVerifier.create(resultService.configureCa(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CA_CONFIG");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("examWeightPercentage");
                })
                .verify();
    }

    // ========================================================================
    // ADDITIONAL SCORE ENTRY VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject null CaScoreRequest")
    void shouldRejectNullCaScoreRequest() {
        StepVerifier.create(resultService.enterCaScores(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null class ID in CaScoreRequest")
    void shouldRejectNullClassIdInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, null, SUBJECT_ID, COMPONENT_ID, 20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.TEN)));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("classId");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null subject ID in CaScoreRequest")
    void shouldRejectNullSubjectIdInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, null, COMPONENT_ID, 20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.TEN)));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("subjectId");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null term ID in CaScoreRequest")
    void shouldRejectNullTermIdInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                null, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.TEN)));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("termId");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject non-positive max score in CaScoreRequest")
    void shouldRejectNonPositiveMaxScoreInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 0,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.TEN)));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("maxScore");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null scores list in CaScoreRequest")
    void shouldRejectNullScoresInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 20, null);
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("scores");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject empty scores list in CaScoreRequest")
    void shouldRejectEmptyScoresInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 20, List.of());
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("scores");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null student ID in CaScoreRequest entry")
    void shouldRejectNullStudentIdInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 20,
                List.of(new CaScoreRequest.ScoreEntry(null, BigDecimal.TEN)));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("scores.studentId");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null score value in CaScoreRequest entry")
    void shouldRejectNullScoreValueInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, null)));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("score");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject negative score value in CaScoreRequest entry")
    void shouldRejectNegativeScoreValueInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.valueOf(-1))));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("score");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject score exceeding max score in CaScoreRequest entry")
    void shouldRejectExceedingScoreValueInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, COMPONENT_ID, 20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.valueOf(21))));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("score");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null component ID in CaScoreRequest")
    void shouldRejectNullComponentIdInCaScoreRequest() {
        CaScoreRequest request = new CaScoreRequest(
                TERM_ID, CLASS_ID, SUBJECT_ID, null, 20,
                List.of(new CaScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.TEN)));
        StepVerifier.create(resultService.enterCaScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("caComponentId");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null ExamScoreRequest")
    void shouldRejectNullExamScoreRequest() {
        StepVerifier.create(resultService.enterExamScores(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null exam ID in ExamScoreRequest")
    void shouldRejectNullExamIdInExamScoreRequest() {
        ExamScoreRequest request = new ExamScoreRequest(
                null, CLASS_ID, SUBJECT_ID, TERM_ID, 100,
                List.of(new ExamScoreRequest.ScoreEntry(STUDENT_ID, BigDecimal.TEN)));
        StepVerifier.create(resultService.enterExamScores(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE_BATCH");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("examId");
                })
                .verify();
    }

    // ========================================================================
    // UPDATE SCORE VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject null score ID in updateScore")
    void shouldRejectNullScoreIdInUpdateScore() {
        StepVerifier.create(resultService.updateScore(null, new UpdateScoreRequest(BigDecimal.TEN, "Reason")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("scoreId");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null request in updateScore")
    void shouldRejectNullRequestInUpdateScore() {
        StepVerifier.create(resultService.updateScore(UUID.randomUUID(), null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("score");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject null score in updateScore request")
    void shouldRejectNullScoreValueInUpdateScore() {
        StepVerifier.create(resultService.updateScore(UUID.randomUUID(), new UpdateScoreRequest(null, "Reason")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("score");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject negative score value in updateScore")
    void shouldRejectNegativeScoreInUpdateScore() {
        StepVerifier.create(resultService.updateScore(UUID.randomUUID(), new UpdateScoreRequest(BigDecimal.valueOf(-5), "Reason")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_SCORE");
                })
                .verify();
    }

    // ========================================================================
    // SHARING STUDENT RESULT
    // ========================================================================

    @Test
    @DisplayName("Should share student result via SMS successfully")
    void shouldShareStudentResultViaSms() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        mockStudentResultGraph();
        when(smsService.send(eq("+2348012345678"), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(resultService.shareStudentResult(
                        STUDENT_ID, TERM_ID, new ShareResultRequest("SMS", "+2348012345678")))
                .assertNext(response -> {
                    assertThat(response.channel()).isEqualTo("SMS");
                    assertThat(response.message()).contains("Result sent to +2348012345678");
                    assertThat(response.shareText()).contains("Test Student - First Term");
                })
                .verifyComplete();

        verify(smsService).send(eq("+2348012345678"), anyString());
    }

    @Test
    @DisplayName("Should share student result via WhatsApp successfully")
    void shouldShareStudentResultViaWhatsApp() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        mockStudentResultGraph();

        StepVerifier.create(resultService.shareStudentResult(
                        STUDENT_ID, TERM_ID, new ShareResultRequest("WHATSAPP", "+2348012345678")))
                .assertNext(response -> {
                    assertThat(response.channel()).isEqualTo("WHATSAPP");
                    assertThat(response.message()).isEqualTo("WhatsApp message prepared");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fallback share student result to Email successfully")
    void shouldShareStudentResultViaEmail() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        mockStudentResultGraph();

        StepVerifier.create(resultService.shareStudentResult(
                        STUDENT_ID, TERM_ID, new ShareResultRequest("EMAIL", "parent@email.com")))
                .assertNext(response -> {
                    assertThat(response.channel()).isEqualTo("EMAIL");
                    assertThat(response.message()).isEqualTo("Email sharing is not configured yet");
                })
                .verifyComplete();
    }

    // ========================================================================
    // DOWNLOAD STUDENT RESULT PDF
    // ========================================================================

    @Test
    @DisplayName("Should download student result PDF data buffer successfully")
    void shouldDownloadStudentResultPdfSuccessfully() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        mockStudentResultGraph();
        byte[] samplePdf = "Sample PDF".getBytes();
        when(resultPdfGenerator.generateStudentResultPdf(any(), any())).thenReturn(samplePdf);

        StepVerifier.create(resultService.downloadStudentResultPdf(STUDENT_ID, TERM_ID))
                .assertNext(dataBuffer -> {
                    assertThat(dataBuffer).isNotNull();
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    assertThat(new String(bytes)).isEqualTo("Sample PDF");
                })
                .verifyComplete();
    }

    // ========================================================================
    // RANKINGS RECOMPUTATION VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject recomputeRankings when class ID is null")
    void shouldRejectRecomputeRankingsWhenClassIdIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        StepVerifier.create(resultService.recomputeRankings(null, TERM_ID, SCHOOL_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_RANKING_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject recomputeRankings when term ID is null")
    void shouldRejectRecomputeRankingsWhenTermIdIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        StepVerifier.create(resultService.recomputeRankings(CLASS_ID, null, SCHOOL_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_RANKING_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject recomputeRankings when class not found")
    void shouldRejectRecomputeRankingsWhenClassNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        when(termRepository.findByIdAndSchoolId(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(resultService.recomputeRankings(CLASS_ID, TERM_ID, SCHOOL_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject recomputeRankings when term not found")
    void shouldRejectRecomputeRankingsWhenTermNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.recomputeRankings(CLASS_ID, TERM_ID, SCHOOL_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    // ========================================================================
    // REPORT CARDS GENERATION VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject generateReportCards when student IDs are empty")
    void shouldRejectGenerateReportCardsWhenStudentIdsEmpty() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        ReportCardRequest request = new ReportCardRequest(TERM_ID, CLASS_ID, null, List.of(), false, false, false, null, null);

        StepVerifier.create(resultService.generateReportCards(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_REPORT_CARD_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject getReportCardJobStatus when job not found")
    void shouldRejectGetReportCardJobStatusWhenNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));

        StepVerifier.create(resultService.getReportCardJobStatus(UUID.randomUUID()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("REPORT_CARD_JOB_NOT_FOUND");
                })
                .verify();
    }

    // ========================================================================
    // COMMENTS AND FEEDBACK
    // ========================================================================

    @Test
    @DisplayName("Should reject addTeacherComment when student not found")
    void shouldRejectAddTeacherCommentWhenStudentNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "Nice"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject addTeacherComment when term not found")
    void shouldRejectAddTeacherCommentWhenTermNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "Nice"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should add teacher comment and update correctly when new comment")
    void shouldAddTeacherCommentWhenCommentNew() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        ReportComment saved = ReportComment.builder()
                .studentId(STUDENT_ID)
                .termId(TERM_ID)
                .schoolId(SCHOOL_ID)
                .teacherComment("Doing well")
                .teacherId(USER_ID)
                .updatedAt(Instant.now())
                .build();
        when(commentRepository.save(any(ReportComment.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "Doing well"))
                .assertNext(response -> {
                    assertThat(response.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.comment()).isEqualTo("Doing well");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should add principal comment and update correctly when new comment")
    void shouldAddPrincipalCommentWhenCommentNew() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        ReportComment saved = ReportComment.builder()
                .studentId(STUDENT_ID)
                .termId(TERM_ID)
                .schoolId(SCHOOL_ID)
                .principalComment("Excellent")
                .principalId(USER_ID)
                .updatedAt(Instant.now())
                .build();
        when(commentRepository.save(any(ReportComment.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, "Excellent"))
                .assertNext(response -> {
                    assertThat(response.studentId()).isEqualTo(STUDENT_ID);
                    assertThat(response.comment()).isEqualTo("Excellent");
                })
                .verifyComplete();
    }

    // ========================================================================
    // RESULTS PUBLICATION VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject unpublishResults when results are not published")
    void shouldRejectUnpublishResultsWhenNotPublished() {
        passThroughTransaction();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_NOT_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject getSubjectsForClass when class not found")
    void shouldRejectGetSubjectsForClassWhenClassNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        StepVerifier.create(resultService.getSubjectsForClass(CLASS_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject getPublishedStudentResults when user is not parent")
    void shouldRejectGetPublishedStudentResultsWhenUserNotParent() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));

        StepVerifier.create(resultService.getPublishedStudentResults(STUDENT_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should get published student results successfully")
    void shouldGetPublishedStudentResultsSuccessfully() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(guardianId, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder()
                        .guardianId(guardianId)
                        .studentId(STUDENT_ID)
                        .canViewResults(true)
                        .build()));

        PublishedResult pr = PublishedResult.builder()
                .schoolId(SCHOOL_ID)
                .termId(TERM_ID)
                .build();
        PublishedResult pr2 = PublishedResult.builder()
                .schoolId(SCHOOL_ID)
                .termId(UUID.fromString("44444444-4444-4444-4444-444444444444"))
                .build();
        when(publishedResultRepository.findBySchoolIdOrderByPublishedAtDesc(SCHOOL_ID))
                .thenReturn(Flux.just(pr, pr2));

        Term t = term();
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(t));
        when(academicSessionRepository.findByIdAndDeletedAtIsNull(SESSION_ID))
                .thenReturn(Mono.just(session()));

        FinalScore fs = finalScore();
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs));

        ClassRanking cr = ranking();
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(cr));

        Term t2 = Term.builder()
                .id(UUID.fromString("44444444-4444-4444-4444-444444444444"))
                .sessionId(null)
                .name("Second Term")
                .build();
        when(termRepository.findByIdAndSchoolId(t2.getId(), SCHOOL_ID)).thenReturn(Mono.just(t2));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, t2.getId(), SCHOOL_ID))
                .thenReturn(Flux.empty());
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, t2.getId(), SCHOOL_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(resultService.getPublishedStudentResults(STUDENT_ID))
                .assertNext(results -> {
                    assertThat(results).hasSize(2);
                    assertThat(results.get(0).termId()).isEqualTo(TERM_ID);
                    assertThat(results.get(0).termName()).isEqualTo("First Term");
                    assertThat(results.get(0).sessionName()).isEqualTo("2025/2026");
                    assertThat(results.get(0).average()).isEqualTo(78.0);
                    assertThat(results.get(0).overallGrade()).isEqualTo("B2");
                    
                    assertThat(results.get(1).termId()).isEqualTo(t2.getId());
                    assertThat(results.get(1).termName()).isEqualTo("Second Term");
                    assertThat(results.get(1).sessionName()).isEmpty();
                    assertThat(results.get(1).average()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should build student result data using reflection")
    void shouldBuildStudentResultDataUsingReflection() throws Exception {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(term()));
        
        FinalScore fs = finalScore();
        when(finalScoreRepository.findByStudentIdAndTermIdOrderBySubjectId(STUDENT_ID, TERM_ID))
                .thenReturn(Flux.just(fs));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Mono.just(subject()));
        when(rankingRepository.findByStudentIdAndTermId(STUDENT_ID, TERM_ID))
                .thenReturn(Mono.just(ranking()));

        java.lang.reflect.Method method = ResultServiceImpl.class.getDeclaredMethod("buildStudentResultData", UUID.class, UUID.class);
        method.setAccessible(true);
        Mono<com.fee.app.schoolfeeapp.result.dto.request.StudentResultData> resultMono = 
                (Mono<com.fee.app.schoolfeeapp.result.dto.request.StudentResultData>) method.invoke(resultService, STUDENT_ID, TERM_ID);

        StepVerifier.create(resultMono)
                .assertNext(data -> {
                    assertThat(data.studentName()).isEqualTo("Test Student");
                    assertThat(data.termName()).isEqualTo("First Term");
                    assertThat(data.subjects()).hasSize(1);
                    assertThat(data.position()).isEqualTo("1 of 1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should build student result data with empty ranking and empty subjects")
    void shouldBuildStudentResultDataWithEmptyRankingAndEmptySubjects() throws Exception {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(term()));
        
        when(finalScoreRepository.findByStudentIdAndTermIdOrderBySubjectId(STUDENT_ID, TERM_ID))
                .thenReturn(Flux.empty());
        when(rankingRepository.findByStudentIdAndTermId(STUDENT_ID, TERM_ID))
                .thenReturn(Mono.empty());

        java.lang.reflect.Method method = ResultServiceImpl.class.getDeclaredMethod("buildStudentResultData", UUID.class, UUID.class);
        method.setAccessible(true);
        Mono<com.fee.app.schoolfeeapp.result.dto.request.StudentResultData> resultMono = 
                (Mono<com.fee.app.schoolfeeapp.result.dto.request.StudentResultData>) method.invoke(resultService, STUDENT_ID, TERM_ID);

        StepVerifier.create(resultMono)
                .assertNext(data -> {
                    assertThat(data.studentName()).isEqualTo("Test Student");
                    assertThat(data.position()).isEqualTo("N/A");
                    assertThat(data.average()).isEqualTo("N/A");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should build student result data with null subject scores")
    void shouldBuildStudentResultDataWithNullSubjectScores() throws Exception {
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findById(TERM_ID)).thenReturn(Mono.just(term()));
        
        FinalScore fs = finalScore();
        fs.setFinalScore(null);
        when(finalScoreRepository.findByStudentIdAndTermIdOrderBySubjectId(STUDENT_ID, TERM_ID))
                .thenReturn(Flux.just(fs));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Mono.just(subject()));
        when(rankingRepository.findByStudentIdAndTermId(STUDENT_ID, TERM_ID))
                .thenReturn(Mono.empty());

        java.lang.reflect.Method method = ResultServiceImpl.class.getDeclaredMethod("buildStudentResultData", UUID.class, UUID.class);
        method.setAccessible(true);
        Mono<com.fee.app.schoolfeeapp.result.dto.request.StudentResultData> resultMono = 
                (Mono<com.fee.app.schoolfeeapp.result.dto.request.StudentResultData>) method.invoke(resultService, STUDENT_ID, TERM_ID);

        StepVerifier.create(resultMono)
                .assertNext(data -> {
                    assertThat(data.average()).isEqualTo("N/A");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should resolve grade bands with various configuration shapes")
    void shouldResolveGradeBandsWithVariousConfigShapes() throws Exception {
        java.lang.reflect.Method method = ResultServiceImpl.class.getDeclaredMethod("resolveGradeBands", com.fasterxml.jackson.databind.JsonNode.class);
        method.setAccessible(true);

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // 1. Null config
        List<?> res1 = (List<?>) method.invoke(resultService, (com.fasterxml.jackson.databind.JsonNode) null);
        assertThat(res1).isNotEmpty(); // Returns default bands

        // 2. Grades is not an array
        com.fasterxml.jackson.databind.JsonNode nodeNotArray = mapper.readTree("{\"grades\": \"not an array\"}");
        List<?> res2 = (List<?>) method.invoke(resultService, nodeNotArray);
        assertThat(res2).isNotEmpty();

        // 3. Grade blank or missing fields
        com.fasterxml.jackson.databind.JsonNode nodeInvalidItems = mapper.readTree("""
                {
                  "grades": [
                    {"grade": " ", "minScore": 70, "maxScore": 100},
                    {"grade": "A", "maxScore": 100},
                    {"grade": "B", "minScore": 50}
                  ]
                }
                """);
        List<?> res3 = (List<?>) method.invoke(resultService, nodeInvalidItems);
        assertThat(res3).isNotEmpty(); // Should return default because configured is empty
    }

    @Test
    @DisplayName("Should get class result sheet with no students")
    void shouldGetClassResultSheetWithNoStudents() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(0L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID)).thenReturn(Flux.empty());
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID)).thenReturn(Flux.empty());
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.students()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should sort class result sheet rows correctly")
    void shouldSortClassResultSheetRowsCorrectly() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(4L));

        UUID s1Id = UUID.randomUUID();
        UUID s2Id = UUID.randomUUID();
        UUID s3Id = UUID.randomUUID();
        UUID s4Id = UUID.randomUUID();

        Student s1 = Student.builder().id(s1Id).firstName("Zoe").lastName("Smith").schoolId(SCHOOL_ID).currentClassId(CLASS_ID).build();
        Student s2 = Student.builder().id(s2Id).firstName("Alex").lastName("Jones").schoolId(SCHOOL_ID).currentClassId(CLASS_ID).build();
        Student s3 = Student.builder().id(s3Id).firstName("Bob").lastName("Miller").schoolId(SCHOOL_ID).currentClassId(CLASS_ID).build();
        Student s4 = Student.builder().id(s4Id).firstName("Charlie").lastName("Baker").schoolId(SCHOOL_ID).currentClassId(CLASS_ID).build();

        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID))
                .thenReturn(Flux.just(s1, s2, s3, s4));

        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID)).thenReturn(Flux.empty());
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());

        ClassRanking r1 = ClassRanking.builder().studentId(s1Id).classPosition(0).build();
        ClassRanking r2 = ClassRanking.builder().studentId(s2Id).classPosition(0).build();
        ClassRanking r3 = ClassRanking.builder().studentId(s3Id).classPosition(1).build();
        ClassRanking r4 = ClassRanking.builder().studentId(s4Id).classPosition(2).build();

        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(r1, r2, r3, r4));

        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(any(UUID.class), eq(TERM_ID), eq(SCHOOL_ID)))
                .thenReturn(Flux.empty());

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.students()).hasSize(4);
                    // Order should be: Bob (pos 1), Charlie (pos 2), Alex (pos 0), Zoe (pos 0)
                    assertThat(response.students().get(0).name()).isEqualTo("Bob Miller");
                    assertThat(response.students().get(1).name()).isEqualTo("Charlie Baker");
                    assertThat(response.students().get(2).name()).isEqualTo("Alex Jones");
                    assertThat(response.students().get(3).name()).isEqualTo("Zoe Smith");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get my children results with null class and unpublished results")
    void shouldGetMyChildrenResultsWithNullClassAndUnpublished() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build()));

        Student s = Student.builder().id(STUDENT_ID).firstName("Test").lastName("Student").currentClassId(null).build();
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(s));
        
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(term()));
        
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.get(0).className()).isEmpty();
                    assertThat(results.get(0).summary()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get my children results when no current term exists")
    void shouldGetMyChildrenResultsWhenNoCurrentTermExists() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build()));

        Student s = Student.builder().id(STUDENT_ID).firstName("Test").lastName("Student").currentClassId(null).build();
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(s));
        
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.get(0).termName()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get my children results with empty scores list")
    void shouldGetMyChildrenResultsWithEmptyScoresList() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build()));

        Student s = Student.builder().id(STUDENT_ID).firstName("Test").lastName("Student").currentClassId(CLASS_ID).build();
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(s));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(classEntity()));
        
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(term()));
        
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().build()));

        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.empty());
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.get(0).summary()).isNull();
                    assertThat(results.get(0).attendance()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get my children results with null final scores")
    void shouldGetMyChildrenResultsWithNullFinalScores() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build()));

        Student s = Student.builder().id(STUDENT_ID).firstName("Test").lastName("Student").currentClassId(CLASS_ID).build();
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(s));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(classEntity()));
        
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(term()));
        
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().build()));

        FinalScore fs = finalScore();
        fs.setFinalScore(null);
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs));
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.get(0).summary()).isNotNull();
                    assertThat(results.get(0).summary().average()).isZero();
                    assertThat(results.get(0).summary().totalSubjects()).isEqualTo(1);
                    assertThat(results.get(0).topSubjects()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update principal comment when record exists")
    void shouldUpdatePrincipalCommentWhenRecordExists() {
        ReportComment existing = comment();
        existing.setPrincipalComment("Old comment");
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(existing));
        when(commentRepository.save(any(ReportComment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, "New comment"))
                .assertNext(response -> {
                    assertThat(response.comment()).isEqualTo("New comment");
                })
                .verifyComplete();

        verify(commentRepository).save(any(ReportComment.class));
    }

    @Test
    @DisplayName("Should recover from duplicate insert race when adding principal comment")
    void shouldRecoverFromDuplicateInsertRaceWhenAddingPrincipalComment() {
        ReportComment existingAfterRace = comment();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty(), Mono.just(existingAfterRace));
        when(commentRepository.save(any(ReportComment.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate")), Mono.just(existingAfterRace));

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, "Great job"))
                .assertNext(response -> {
                    assertThat(response.comment()).isEqualTo("Great job");
                })
                .verifyComplete();

        verify(commentRepository, times(2)).save(any(ReportComment.class));
    }

    @Test
    @DisplayName("Should filter out invalid guardian links in getMyChildrenResults")
    void shouldFilterOutInvalidGuardianLinks() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(
                        StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(null).canViewResults(true).build(),
                        StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(false).build()
                ));

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should configure grading rules when config is null")
    void shouldConfigureGradingRulesWhenConfigIsNull() throws Exception {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        GradingRuleRequest request = new GradingRuleRequest(null);
        when(gradeConfigRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Mono.empty());
        
        GradeConfig savedEntity = GradeConfig.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .config(null)
                .isActive(true)
                .build();
        when(gradeConfigRepository.save(any(GradeConfig.class))).thenReturn(Mono.just(savedEntity));

        StepVerifier.create(resultService.configureGradingRules(request))
                .assertNext(response -> {
                    assertThat(response.gradesCount()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should lookup CA components with null weight percentage")
    void shouldLookupCaComponentsWithNullWeight() {
        CaComponent caComponent = CaComponent.builder()
                .id(COMPONENT_ID)
                .schoolId(SCHOOL_ID)
                .name("First CA")
                .maxScore(20)
                .weightPercentage(null)
                .isActive(true)
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(caComponentRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID))
                .thenReturn(Flux.just(caComponent));

        StepVerifier.create(resultService.getCaComponents())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.get(0).weightPercentage()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should auto-create default exam with fallback weight when examWeightPercentage is null")
    void shouldAutoCreateDefaultExamWithFallbackWeight() {
        ResultServiceImpl spyService = org.mockito.Mockito.spy(resultService);
        
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(examRepository.findByTermId(TERM_ID)).thenReturn(Flux.empty());
        org.mockito.Mockito.doReturn(Mono.just(new com.fee.app.schoolfeeapp.result.dto.response.CaConfigResponse(0, null, "message"))).when(spyService).getCaConfig();
        
        User dbUser = User.builder().id(UUID.randomUUID()).keycloakId(USER_ID).build();
        when(userRepository.findByKeycloakIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(dbUser));
        
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(spyService.getExamsForTerm(TERM_ID))
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should publish results and handle null publishedAt gracefully")
    void shouldPublishResultsAndHandleNullPublishedAt() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID)).thenReturn(Mono.empty());
        
        PublishedResult savedResult = PublishedResult.builder()
                .schoolId(SCHOOL_ID)
                .termId(TERM_ID)
                .publishedAt(null)
                .build();
        when(publishedResultRepository.save(any(PublishedResult.class)))
                .thenReturn(Mono.just(savedResult));

        StepVerifier.create(resultService.publishResults(TERM_ID))
                .assertNext(response -> {
                    assertThat(response.publishedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should add teacher comment and handle null updatedAt")
    void shouldAddTeacherCommentAndHandleNullUpdatedAt() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());

        ReportComment saved = ReportComment.builder()
                .studentId(STUDENT_ID)
                .termId(TERM_ID)
                .schoolId(SCHOOL_ID)
                .teacherComment("Doing well")
                .updatedAt(null)
                .build();
        when(commentRepository.save(any(ReportComment.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, "Doing well"))
                .assertNext(response -> {
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get student result with null subject position")
    void shouldGetStudentResultWithNullSubjectPosition() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        mockStudentResultGraph();
        
        FinalScore fs = finalScore();
        fs.setSubjectPosition(null);
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs));

        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndStudentIdAndDeletedAtIsNull(guardianId, STUDENT_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder()
                        .guardianId(guardianId)
                        .studentId(STUDENT_ID)
                        .canViewResults(true)
                        .build()));
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().schoolId(SCHOOL_ID).termId(TERM_ID).build()));

        StepVerifier.create(resultService.getStudentResult(STUDENT_ID, TERM_ID))
                .assertNext(result -> {
                    assertThat(result.subjects().get(0).subjectPosition()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get class result sheet when ranking is null")
    void shouldGetClassResultSheetWhenRankingIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID)).thenReturn(Flux.just(student()));
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID)).thenReturn(Flux.empty());
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.empty());
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.students().get(0).position()).isZero();
                    assertThat(response.students().get(0).average()).isZero();
                    assertThat(response.students().get(0).overallGrade()).isEqualTo("F9");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get class result sheet when average percentage is null")
    void shouldGetClassResultSheetWhenAveragePercentageIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID)).thenReturn(Flux.just(student()));
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID)).thenReturn(Flux.empty());
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        
        ClassRanking r = ranking();
        r.setAveragePercentage(null);
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(r));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.students().get(0).average()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get class result sheet when overall grade is blank")
    void shouldGetClassResultSheetWhenOverallGradeIsBlank() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID)).thenReturn(Flux.just(student()));
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID)).thenReturn(Flux.empty());
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        
        ClassRanking r = ranking();
        r.setOverallGrade(" ");
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(r));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.students().get(0).overallGrade()).isEqualTo("A1");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get class result sheet when subject position is null")
    void shouldGetClassResultSheetWhenSubjectPositionIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID)).thenReturn(Flux.just(student()));

        ClassSubject cs = ClassSubject.builder().classId(CLASS_ID).subjectId(SUBJECT_ID).isActive(true).build();
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID)).thenReturn(Flux.just(cs));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Mono.just(subject()));

        ClassRanking r = ranking();
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(r));
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        
        FinalScore fs = finalScore();
        fs.setSubjectPosition(null);
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs));

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.students().get(0).subjects().get(0).position()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get my children results with null attendance fields")
    void shouldGetMyChildrenResultsWithNullAttendanceFields() {
        UUID guardianId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianRepository.findByKeycloakId(USER_ID))
                .thenReturn(Mono.just(StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build()));
        when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Flux.just(StudentGuardianLinkProjection.builder().guardianId(guardianId).studentId(STUDENT_ID).canViewResults(true).build()));

        Student s = Student.builder().id(STUDENT_ID).firstName("Test").lastName("Student").currentClassId(CLASS_ID).build();
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(s));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID))
                .thenReturn(Mono.just(classEntity()));
        
        when(termRepository.findCurrentTermsBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(term()));
        
        when(publishedResultRepository.findBySchoolIdAndTermId(SCHOOL_ID, TERM_ID))
                .thenReturn(Mono.just(PublishedResult.builder().build()));

        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(finalScore()));
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(ranking()));
        
        ReportComment rc = comment();
        rc.setAttendanceDaysOpen(null);
        rc.setAttendanceDaysPresent(null);
        rc.setAttendanceDaysAbsent(null);
        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.just(rc));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Mono.just(subject()));

        StepVerifier.create(resultService.getMyChildrenResults())
                .assertNext(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.get(0).attendance().daysOpen()).isZero();
                    assertThat(results.get(0).attendance().daysPresent()).isZero();
                    assertThat(results.get(0).attendance().daysAbsent()).isZero();
                    assertThat(results.get(0).attendance().attendanceRate()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get class result sheet when student final score is null")
    void shouldGetClassResultSheetWhenStudentFinalScoreIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.countActiveByCurrentClassId(CLASS_ID)).thenReturn(Mono.just(1L));
        when(studentRepository.findActiveBySchoolIdAndCurrentClassId(SCHOOL_ID, CLASS_ID)).thenReturn(Flux.just(student()));

        ClassSubject cs = ClassSubject.builder().classId(CLASS_ID).subjectId(SUBJECT_ID).isActive(true).build();
        when(classSubjectRepository.findByClassIdAndIsActiveTrue(CLASS_ID)).thenReturn(Flux.just(cs));
        when(subjectRepository.findById(SUBJECT_ID)).thenReturn(Mono.just(subject()));

        ClassRanking r = ranking();
        when(rankingRepository.findByClassIdAndTermIdOrderByClassPosition(CLASS_ID, TERM_ID))
                .thenReturn(Flux.just(r));
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());
        
        FinalScore fs = finalScore();
        fs.setFinalScore(null);
        fs.setGrade(null);
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs));

        StepVerifier.create(resultService.getClassResultSheet(CLASS_ID, TERM_ID))
                .assertNext(response -> {
                    assertThat(response.students().get(0).subjects().get(0).finalScore()).isZero();
                    assertThat(response.students().get(0).subjects().get(0).grade()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should validate TraitAssessmentRequest validation exceptions")
    void shouldValidateTraitAssessmentRequestExceptions() {
        // null request
        StepVerifier.create(resultService.saveTraitAssessments(null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("Trait assessment request is required"))
                .verify();

        // null classId
        TraitAssessmentRequest r1 = new TraitAssessmentRequest(null, TERM_ID, List.of());
        StepVerifier.create(resultService.saveTraitAssessments(r1))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("Class is required"))
                .verify();

        // null termId
        TraitAssessmentRequest r2 = new TraitAssessmentRequest(CLASS_ID, null, List.of());
        StepVerifier.create(resultService.saveTraitAssessments(r2))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("Term is required"))
                .verify();

        // null entries
        TraitAssessmentRequest r3 = new TraitAssessmentRequest(CLASS_ID, TERM_ID, null);
        StepVerifier.create(resultService.saveTraitAssessments(r3))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("At least one assessment rating is required"))
                .verify();

        // empty entries
        TraitAssessmentRequest r4 = new TraitAssessmentRequest(CLASS_ID, TERM_ID, List.of());
        StepVerifier.create(resultService.saveTraitAssessments(r4))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("At least one assessment rating is required"))
                .verify();

        // studentId or traitId null in entry
        TraitAssessmentRequest.Entry entry1 = new TraitAssessmentRequest.Entry(null, UUID.randomUUID(), "A", "Good");
        TraitAssessmentRequest r5 = new TraitAssessmentRequest(CLASS_ID, TERM_ID, List.of(entry1));
        StepVerifier.create(resultService.saveTraitAssessments(r5))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("Student and trait are required"))
                .verify();

        // rating null or blank
        TraitAssessmentRequest.Entry entry2 = new TraitAssessmentRequest.Entry(STUDENT_ID, UUID.randomUUID(), "   ", "Good");
        TraitAssessmentRequest r6 = new TraitAssessmentRequest(CLASS_ID, TERM_ID, List.of(entry2));
        StepVerifier.create(resultService.saveTraitAssessments(r6))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("Rating is required"))
                .verify();

        // duplicate entry
        UUID traitId = UUID.randomUUID();
        TraitAssessmentRequest.Entry entry3 = new TraitAssessmentRequest.Entry(STUDENT_ID, traitId, "A", "Good");
        TraitAssessmentRequest.Entry entry4 = new TraitAssessmentRequest.Entry(STUDENT_ID, traitId, "B", "Nice");
        TraitAssessmentRequest r7 = new TraitAssessmentRequest(CLASS_ID, TERM_ID, List.of(entry3, entry4));
        StepVerifier.create(resultService.saveTraitAssessments(r7))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(SchoolFeeException.class)
                        .hasMessageContaining("Each student/trait can appear only once"))
                .verify();
    }

    @Test
    @DisplayName("Should fail when class or term is not found during trait assessment save")
    void shouldFailWhenClassOrTermNotFoundInTraitAssessmentSave() {
        passThroughTransaction();
        UUID traitId = UUID.randomUUID();
        TraitAssessmentRequest.Entry entry = new TraitAssessmentRequest.Entry(STUDENT_ID, traitId, "A", "Good");
        TraitAssessmentRequest request = new TraitAssessmentRequest(CLASS_ID, TERM_ID, List.of(entry));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(userRepository.findByKeycloakIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Mono.just(User.builder().id(USER_ID).build()));

        // Class not found
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        StepVerifier.create(resultService.saveTraitAssessments(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CLASS_NOT_FOUND");
                })
                .verify();

        // Term not found
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        StepVerifier.create(resultService.saveTraitAssessments(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should fail when student or trait is not found or student not in class during verification")
    void shouldFailWhenStudentOrTraitNotFoundDuringVerification() {
        passThroughTransaction();
        UUID traitId = UUID.randomUUID();
        TraitAssessmentRequest.Entry entry = new TraitAssessmentRequest.Entry(STUDENT_ID, traitId, "A", "Good");
        TraitAssessmentRequest request = new TraitAssessmentRequest(CLASS_ID, TERM_ID, List.of(entry));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(userRepository.findByKeycloakIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Mono.just(User.builder().id(USER_ID).build()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));

        // Student not found
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.empty());
        StepVerifier.create(resultService.saveTraitAssessments(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");
                })
                .verify();

        // Student not in class
        Student wrongClassStudent = student();
        wrongClassStudent.setCurrentClassId(UUID.randomUUID());
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(wrongClassStudent));
        StepVerifier.create(resultService.saveTraitAssessments(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_IN_CLASS");
                })
                .verify();

        // Trait not found
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(assessmentTraitRepository.findActiveByIdAndSchoolId(traitId, SCHOOL_ID)).thenReturn(Mono.empty());
        StepVerifier.create(resultService.saveTraitAssessments(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ASSESSMENT_TRAIT_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should save trait assessments successfully")
    void shouldSaveTraitAssessmentsSuccessfully() {
        passThroughTransaction();
        UUID traitId1 = UUID.randomUUID();
        UUID traitId2 = UUID.randomUUID();
        TraitAssessmentRequest.Entry entry = new TraitAssessmentRequest.Entry(STUDENT_ID, traitId1, "A", "Good");
        TraitAssessmentRequest.Entry entryWithNoComment = new TraitAssessmentRequest.Entry(STUDENT_ID, traitId2, "B", "");
        TraitAssessmentRequest request = new TraitAssessmentRequest(CLASS_ID, TERM_ID, List.of(entry, entryWithNoComment));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(userRepository.findByKeycloakIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Mono.just(User.builder().id(USER_ID).build()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID)).thenReturn(Mono.just(student()));
        when(assessmentTraitRepository.findActiveByIdAndSchoolId(traitId1, SCHOOL_ID))
                .thenReturn(Mono.just(AssessmentTrait.builder().id(traitId1).build()));
        when(assessmentTraitRepository.findActiveByIdAndSchoolId(traitId2, SCHOOL_ID))
                .thenReturn(Mono.just(AssessmentTrait.builder().id(traitId2).build()));
        when(studentTraitAssessmentRepository.upsert(any(StudentTraitAssessment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(resultService.saveTraitAssessments(request))
                .assertNext(response -> {
                    assertThat(response.assessmentsSaved()).isEqualTo(2);
                    assertThat(response.message()).isEqualTo("Trait assessments saved");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get assessment traits and default them if missing")
    void shouldGetAssessmentTraitsAndDefaultIfMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        
        ClassEntity cEntity = classEntity();
        cEntity.setGradeLevel("JUNIOR_SECONDARY");
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(cEntity));

        // Traits exist
        AssessmentTrait existingTrait = AssessmentTrait.builder()
                .id(UUID.randomUUID())
                .name("Punctuality")
                .category("AFFECTIVE")
                .sortOrder(1)
                .build();
        when(assessmentTraitRepository.findActiveForLevel(SCHOOL_ID, "JUNIOR_SECONDARY"))
                .thenReturn(Flux.just(existingTrait));

        StepVerifier.create(resultService.getAssessmentTraits(CLASS_ID))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).name()).isEqualTo("Punctuality");
                })
                .verifyComplete();

        // Traits missing, insert default ones (using ClassEntity with null gradeLevel to hit fallback)
        ClassEntity nullGradeClass = classEntity();
        nullGradeClass.setGradeLevel(null);
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(nullGradeClass));
        when(assessmentTraitRepository.findActiveForLevel(SCHOOL_ID, "PRIMARY"))
                .thenReturn(Flux.empty()) // First call returns empty
                .thenReturn(Flux.just(existingTrait)); // Second call returns the populated list
        when(assessmentTraitRepository.insertDefaultIfMissing(eq(SCHOOL_ID), eq("PRIMARY"), anyString(), anyString(), any(Integer.class), any(Instant.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(resultService.getAssessmentTraits(CLASS_ID))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should get trait assessment values successfully")
    void shouldGetTraitAssessmentValuesSuccessfully() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));

        StudentTraitAssessmentValueView val = new StudentTraitAssessmentValueView(
                STUDENT_ID, UUID.randomUUID(), "A", "Good work"
        );
        when(studentTraitAssessmentRepository.findValuesForClass(CLASS_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(val));

        StepVerifier.create(resultService.getTraitAssessmentValues(CLASS_ID, TERM_ID))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).studentId()).isEqualTo(STUDENT_ID);
                    assertThat(list.get(0).rating()).isEqualTo("A");
                    assertThat(list.get(0).comment()).isEqualTo("Good work");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should generate automatic comments for various averages")
    void shouldGenerateAutomaticCommentsForVariousAverages() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(userRepository.findByKeycloakIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Mono.just(User.builder().id(USER_ID).build()));
        
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(student()));
        when(termRepository.findByIdAndSchoolId(TERM_ID, SCHOOL_ID)).thenReturn(Mono.just(term()));
        when(classRepository.findByIdAndSchoolId(CLASS_ID, SCHOOL_ID)).thenReturn(Mono.just(classEntity()));
        when(rankingRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty()); // empty ranking to force average calculation from final scores
        when(gradeConfigRepository.findBySchoolIdAndIsActiveTrue(SCHOOL_ID)).thenReturn(Mono.empty());

        when(commentRepository.findByStudentIdAndTermIdAndSchoolId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(commentRepository.save(any(ReportComment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Test case 1: average 80 (excellent)
        FinalScore fs1 = finalScore();
        fs1.setFinalScore(BigDecimal.valueOf(80));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs1));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("excellent performance");
                    assertThat(res.comment()).contains("Encourage this learner");
                })
                .verifyComplete();

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("This is commendable");
                })
                .verifyComplete();

        // Test case 2: average 70 (very well)
        FinalScore fs2 = finalScore();
        fs2.setFinalScore(BigDecimal.valueOf(70));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs2));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("performed very well");
                    assertThat(res.comment()).contains("continued focus");
                })
                .verifyComplete();

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("very good result");
                })
                .verifyComplete();

        // Test case 3: average 55 (satisfactory)
        FinalScore fs3 = finalScore();
        fs3.setFinalScore(BigDecimal.valueOf(55));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs3));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("satisfactory progress");
                    assertThat(res.comment()).contains("consistent revision");
                })
                .verifyComplete();

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("fair result");
                })
                .verifyComplete();

        // Test case 4: average 45 (progress but needs consistency)
        FinalScore fs4 = finalScore();
        fs4.setFinalScore(BigDecimal.valueOf(45));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs4));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("needs more consistency");
                    assertThat(res.comment()).contains("Close monitoring");
                })
                .verifyComplete();

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("Promotion should be supported");
                })
                .verifyComplete();

        // Test case 5: average 30 (urgent support)
        FinalScore fs5 = finalScore();
        fs5.setFinalScore(BigDecimal.valueOf(30));
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fs5));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("needs urgent academic support");
                    assertThat(res.comment()).contains("improvement plan");
                })
                .verifyComplete();

        StepVerifier.create(resultService.addPrincipalComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("Immediate intervention");
                })
                .verifyComplete();

        // Test case 6: average empty (averageScore returns 0) - throws exception because ranking is also empty
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.empty());

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, null, true))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULT_SCORES_NOT_FOUND");
                })
                .verify();

        // Test case 7: average list has null final scores
        FinalScore fsNull = finalScore();
        fsNull.setFinalScore(null);
        when(finalScoreRepository.findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(STUDENT_ID, TERM_ID, SCHOOL_ID))
                .thenReturn(Flux.just(fsNull));

        StepVerifier.create(resultService.addTeacherComment(STUDENT_ID, TERM_ID, null, true))
                .assertNext(res -> {
                    assertThat(res.comment()).contains("needs urgent academic support");
                })
                .verifyComplete();
    }
}
