package com.fee.app.schoolfeeapp.result.service.impl;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.result.dto.request.CaConfigRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ExamScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.request.ReportCardRequest;
import com.fee.app.schoolfeeapp.result.dto.request.GradingRuleRequest;
import com.fee.app.schoolfeeapp.result.dto.response.CaScoreRequest;
import com.fee.app.schoolfeeapp.result.dto.response.PublishResultResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ReportCardJobResponse;
import com.fee.app.schoolfeeapp.result.dto.response.ReportCommentResponse;
import com.fee.app.schoolfeeapp.result.dto.response.UpdateScoreRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ResultServiceImplIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        registry.add("spring.flyway.url", () ->
                String.format("jdbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private ResultServiceImpl resultService;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SESSION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID TERM_ID = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final UUID CLASS_ID = UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final UUID SUBJECT_ID = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb");
    private static final UUID EXAM_ID = UUID.fromString("99999999-aaaa-bbbb-cccc-dddddddddddd");
    private static final UUID COMPONENT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID STUDENT_ID = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
    private static final UUID SCORE_ID = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000000");
    private static final UUID GUARDIAN_ID = UUID.fromString("dddddddd-eeee-ffff-0000-111111111111");

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(jwtUtils, keycloakAdminService, reactiveJwtDecoder);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Should replace active CA config without leaving duplicate active rows")
    void shouldReplaceActiveCaConfigWithoutLeavingDuplicateActiveRows() {
        seedSchool();

        StepVerifier.create(resultService.configureCa(validRequest()))
                .assertNext(response -> {
                    assertThat(response.componentCount()).isEqualTo(2);
                    assertThat(response.examWeightPercentage()).isEqualTo(60.0);
                })
                .verifyComplete();

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.ca_components
                WHERE school_id = :schoolId AND is_active = true
                """, Map.of("schoolId", SCHOOL_ID))).isEqualTo(2);

        StepVerifier.create(resultService.configureCa(new CaConfigRequest(
                        List.of(new CaConfigRequest.CaComponentRequest("Project", 10, 20, 1)),
                        80)))
                .assertNext(response -> {
                    assertThat(response.componentCount()).isEqualTo(1);
                    assertThat(response.examWeightPercentage()).isEqualTo(80.0);
                })
                .verifyComplete();

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.ca_components
                WHERE school_id = :schoolId AND is_active = true
                """, Map.of("schoolId", SCHOOL_ID))).isEqualTo(1);
        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.ca_components
                WHERE school_id = :schoolId
                """, Map.of("schoolId", SCHOOL_ID))).isEqualTo(3);
        assertThat(fetchOne("""
                SELECT name
                FROM result.ca_components
                WHERE school_id = :schoolId AND is_active = true
                """, Map.of("schoolId", SCHOOL_ID))).containsEntry("name", "Project");
    }

    @Test
    @DisplayName("Should get current CA config with computed exam weight")
    void shouldGetCurrentCaConfigWithComputedExamWeight() {
        seedSchool();
        seedCaComponent("First Test", 20, BigDecimal.valueOf(15), 1, true);
        seedCaComponent("Second Test", 20, BigDecimal.valueOf(25), 2, true);
        seedCaComponent("Old Test", 20, BigDecimal.valueOf(20), 3, false);

        StepVerifier.create(resultService.getCaConfig())
                .assertNext(response -> {
                    assertThat(response.componentCount()).isEqualTo(2);
                    assertThat(response.examWeightPercentage()).isEqualTo(60.0);
                    assertThat(response.message()).isEqualTo("Current CA configuration");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject replacing CA config when active components have scores")
    void shouldRejectReplacingCaConfigWhenActiveComponentsHaveScores() {
        seedSchool();
        UUID componentId = seedCaComponent("First Test", 20, BigDecimal.valueOf(20), 1, true);
        seedCaScoreFixture(componentId);

        StepVerifier.create(resultService.configureCa(validRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CA_CONFIG_IN_USE");
                })
                .verify();

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.ca_components
                WHERE school_id = :schoolId AND is_active = true
                """, Map.of("schoolId", SCHOOL_ID))).isEqualTo(1);
    }

    @Test
    @DisplayName("Should require school context")
    void shouldRequireSchoolContext() {
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
    }

    @Test
    @DisplayName("Should enter CA scores and reject duplicate score races")
    void shouldEnterCaScoresAndRejectDuplicateScoreRaces() {
        seedScoreEntryFixture();
        seedCaComponentWithId(COMPONENT_ID, "First Test", 20, BigDecimal.valueOf(20), 1, true);

        StepVerifier.create(resultService.enterCaScores(validCaScoreRequest()))
                .assertNext(response -> assertThat(response.scoresEntered()).isEqualTo(1))
                .verifyComplete();

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.ca_scores
                WHERE student_id = :studentId AND ca_component_id = :componentId
                """, Map.of("studentId", STUDENT_ID, "componentId", COMPONENT_ID))).isEqualTo(1);

        StepVerifier.create(resultService.enterCaScores(validCaScoreRequest()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCORE_ALREADY_EXISTS");
                })
                .verify();
    }

    @Test
    @DisplayName("Should enter exam scores and compute final score and ranking")
    void shouldEnterExamScoresAndComputeFinalScoreAndRanking() {
        seedScoreEntryFixture();
        seedExam();

        StepVerifier.create(resultService.enterExamScores(validExamScoreRequest()))
                .assertNext(response -> {
                    assertThat(response.scoresEntered()).isEqualTo(1);
                    assertThat(response.finalScoresComputed()).isEqualTo(1);
                })
                .verifyComplete();

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.scores
                WHERE student_id = :studentId AND exam_id = :examId
                """, Map.of("studentId", STUDENT_ID, "examId", EXAM_ID))).isEqualTo(1);
        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.final_scores
                WHERE student_id = :studentId AND term_id = :termId
                """, Map.of("studentId", STUDENT_ID, "termId", TERM_ID))).isEqualTo(1);
        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.class_rankings
                WHERE student_id = :studentId AND term_id = :termId
                """, Map.of("studentId", STUDENT_ID, "termId", TERM_ID))).isEqualTo(1);
        assertThat(fetchOne("""
                SELECT grade, remark
                FROM result.final_scores
                WHERE student_id = :studentId AND term_id = :termId
                """, Map.of("studentId", STUDENT_ID, "termId", TERM_ID)))
                .containsEntry("grade", "F9")
                .containsEntry("remark", "Fail");
        assertThat(fetchOne("""
                SELECT overall_grade
                FROM result.class_rankings
                WHERE student_id = :studentId AND term_id = :termId
                """, Map.of("studentId", STUDENT_ID, "termId", TERM_ID)))
                .containsEntry("overall_grade", "F9");
    }

    @Test
    @DisplayName("Should update score and create audit log")
    void shouldUpdateScoreAndCreateAuditLog() {
        seedScoreEntryFixture();
        seedExam();
        seedExamScore(BigDecimal.valueOf(55));

        StepVerifier.create(resultService.updateScore(SCORE_ID, new UpdateScoreRequest(BigDecimal.valueOf(70), "Correction")))
                .assertNext(response -> {
                    assertThat(response.previousScore()).isEqualByComparingTo("55");
                    assertThat(response.newScore()).isEqualByComparingTo("70");
                })
                .verifyComplete();

        assertThat(fetchOne("""
                SELECT score
                FROM result.scores
                WHERE id = :scoreId
                """, Map.of("scoreId", SCORE_ID))).containsEntry("score", BigDecimal.valueOf(70.0));
        assertThat(fetchOne("""
                SELECT school_id, old_score, new_score
                FROM result.score_audit_log
                WHERE score_id = :scoreId
                """, Map.of("scoreId", SCORE_ID)))
                .containsEntry("school_id", SCHOOL_ID)
                .containsEntry("old_score", BigDecimal.valueOf(55.0))
                .containsEntry("new_score", BigDecimal.valueOf(70.0));
    }

    @Test
    @DisplayName("Should reject updating score after results are published")
    void shouldRejectUpdatingScoreAfterResultsArePublished() {
        seedScoreEntryFixture();
        seedExam();
        seedExamScore(BigDecimal.valueOf(55));
        seedPublishedResult();

        StepVerifier.create(resultService.updateScore(SCORE_ID, new UpdateScoreRequest(BigDecimal.valueOf(70), "Correction")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_ALREADY_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should build student result for parent when published")
    void shouldBuildStudentResultForParentWhenPublished() {
        seedScoreEntryFixture();
        seedCaComponentWithId(COMPONENT_ID, "First Test", 20, BigDecimal.valueOf(20), 1, true);
        seedCaScore(COMPONENT_ID, BigDecimal.valueOf(18), 20);
        seedFinalScore();
        seedRanking();
        seedReportComment();
        seedGuardianLink(true);
        seedPublishedResult();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

        StepVerifier.create(resultService.getStudentResult(STUDENT_ID, TERM_ID))
                .assertNext(result -> {
                    assertThat(result.student().studentId()).isEqualTo(STUDENT_ID);
                    assertThat(result.student().fullName()).isEqualTo("Test Student");
                    assertThat(result.subjects()).hasSize(1);
                    assertThat(result.subjects().getFirst().caScores()).hasSize(1);
                    assertThat(result.summary().subjectsTaken()).isEqualTo(1);
                    assertThat(result.teacherComment()).isEqualTo("Doing well");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject parent student result before publication")
    void shouldRejectParentStudentResultBeforePublication() {
        seedScoreEntryFixture();
        seedGuardianLink(true);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

        StepVerifier.create(resultService.getStudentResult(STUDENT_ID, TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_NOT_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should generate report cards and return retrievable job status")
    void shouldGenerateReportCardsAndReturnRetrievableJobStatus() {
        seedScoreEntryFixture();
        ReportCardRequest request = new ReportCardRequest(
                TERM_ID,
                CLASS_ID,
                List.of(STUDENT_ID, STUDENT_ID),
                true,
                true,
                true,
                "Great effort",
                "PDF");

        ReportCardJobResponse generated = resultService.generateReportCards(request).block();

        assertThat(generated).isNotNull();
        assertThat(generated.status()).isEqualTo("PROCESSING");
        assertThat(generated.totalStudents()).isEqualTo(1);

        StepVerifier.create(resultService.getReportCardJobStatus(generated.jobId()))
                .assertNext(job -> {
                    assertThat(job.jobId()).isEqualTo(generated.jobId());
                    assertThat(job.status()).isEqualTo("COMPLETED");
                    assertThat(job.totalStudents()).isEqualTo(1);
                    assertThat(job.completedStudents()).isEqualTo(1);
                    assertThat(job.failedStudents()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject unknown report card job")
    void shouldRejectUnknownReportCardJob() {
        StepVerifier.create(resultService.getReportCardJobStatus(UUID.randomUUID()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("REPORT_CARD_JOB_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should publish then unpublish results for term")
    void shouldPublishThenUnpublishResultsForTerm() {
        seedScoreEntryFixture();

        PublishResultResponse published = resultService.publishResults(TERM_ID).block();
        assertThat(published).isNotNull();
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.published_results WHERE school_id = :schoolId AND term_id = :termId",
                Map.of("schoolId", SCHOOL_ID, "termId", TERM_ID))).isEqualTo(1L);

        PublishResultResponse unpublished = resultService.unpublishResults(TERM_ID).block();
        assertThat(unpublished).isNotNull();
        assertThat(unpublished.status()).isEqualTo("UNPUBLISHED");
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.published_results WHERE school_id = :schoolId AND term_id = :termId",
                Map.of("schoolId", SCHOOL_ID, "termId", TERM_ID))).isZero();
    }

    @Test
    @DisplayName("Should reject publishing results when term does not exist in school")
    void shouldRejectPublishingResultsWhenTermDoesNotExistInSchool() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");
        seedSession();

        StepVerifier.create(resultService.publishResults(UUID.randomUUID()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject unpublishing results when not published")
    void shouldRejectUnpublishingResultsWhenNotPublished() {
        seedScoreEntryFixture();

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_NOT_PUBLISHED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject unpublishing when term does not exist in school")
    void shouldRejectUnpublishingWhenTermDoesNotExistInSchool() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");
        seedSession();

        StepVerifier.create(resultService.unpublishResults(TERM_ID))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should allow re-publishing after unpublish")
    void shouldAllowRePublishingAfterUnpublish() {
        seedScoreEntryFixture();

        // First publish
        PublishResultResponse firstPublish = resultService.publishResults(TERM_ID).block();
        assertThat(firstPublish).isNotNull();
        assertThat(firstPublish.status()).isEqualTo("PUBLISHED");
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.published_results WHERE school_id = :schoolId AND term_id = :termId",
                Map.of("schoolId", SCHOOL_ID, "termId", TERM_ID))).isEqualTo(1L);

        // Unpublish
        PublishResultResponse unpublished = resultService.unpublishResults(TERM_ID).block();
        assertThat(unpublished).isNotNull();
        assertThat(unpublished.status()).isEqualTo("UNPUBLISHED");
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.published_results WHERE school_id = :schoolId AND term_id = :termId",
                Map.of("schoolId", SCHOOL_ID, "termId", TERM_ID))).isZero();

        // Re-publish
        PublishResultResponse rePublished = resultService.publishResults(TERM_ID).block();
        assertThat(rePublished).isNotNull();
        assertThat(rePublished.status()).isEqualTo("PUBLISHED");
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.published_results WHERE school_id = :schoolId AND term_id = :termId",
                Map.of("schoolId", SCHOOL_ID, "termId", TERM_ID))).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should allow score modification after unpublish")
    void shouldAllowScoreModificationAfterUnpublish() {
        seedScoreEntryFixture();
        seedExam();
        seedExamScore(BigDecimal.valueOf(55));

        // Publish results
        resultService.publishResults(TERM_ID).block();

        // Verify score update is rejected while published
        StepVerifier.create(resultService.updateScore(SCORE_ID, new UpdateScoreRequest(BigDecimal.valueOf(70), "Correction")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RESULTS_ALREADY_PUBLISHED");
                })
                .verify();

        // Unpublish
        resultService.unpublishResults(TERM_ID).block();

        // Score update should now succeed
        StepVerifier.create(resultService.updateScore(SCORE_ID, new UpdateScoreRequest(BigDecimal.valueOf(70), "Correction after unpublish")))
                .assertNext(response -> {
                    assertThat(response.previousScore()).isEqualByComparingTo("55");
                    assertThat(response.newScore()).isEqualByComparingTo("70");
                })
                .verifyComplete();

        // Verify updated score in DB
        assertThat(fetchOne(
                "SELECT score FROM result.scores WHERE id = :scoreId",
                Map.of("scoreId", SCORE_ID))).containsEntry("score", BigDecimal.valueOf(70.0));
    }

    @Test
    @DisplayName("Should return grading rules after configuring them")
    void shouldReturnGradingRulesAfterConfiguring() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");
        seedGradeConfig("[{\"grade\":\"A\",\"min\":70,\"max\":100},{\"grade\":\"B\",\"min\":60,\"max\":69}]");

        StepVerifier.create(resultService.getGradingRules())
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isEqualTo(2);
                    assertThat(response.message()).isEqualTo("Current grading rules");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return default response when no grading rules configured")
    void shouldReturnDefaultResponseWhenNoGradingRulesConfigured() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");

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
    }

    @Test
    @DisplayName("Should configure grading rules when no previous config exists")
    void shouldConfigureGradingRulesWhenNoPreviousConfigExists() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");

        // Verify no config exists initially
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.grade_configs WHERE school_id = :schoolId",
                Map.of("schoolId", SCHOOL_ID))).isZero();

        seedGradeConfig("[{\"grade\":\"A\",\"min\":70,\"max\":100},{\"grade\":\"B\",\"min\":60,\"max\":69}]");

        // Verify config was saved
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.grade_configs WHERE school_id = :schoolId",
                Map.of("schoolId", SCHOOL_ID))).isEqualTo(1L);

        // Verify we can read it back via getGradingRules
        StepVerifier.create(resultService.getGradingRules())
                .assertNext(response -> {
                    assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(response.gradesCount()).isEqualTo(2);
                    assertThat(response.message()).isEqualTo("Current grading rules");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update grading rules when config already exists")
    void shouldUpdateGradingRulesWhenConfigAlreadyExists() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");

        // Seed initial config with 2 grades
        seedGradeConfig("[{\"grade\":\"A\",\"min\":70,\"max\":100},{\"grade\":\"B\",\"min\":60,\"max\":69}]");

        // Verify initial state
        StepVerifier.create(resultService.getGradingRules())
                .assertNext(response -> assertThat(response.gradesCount()).isEqualTo(2))
                .verifyComplete();

        // Update to 3 grades by inserting a new config directly
        databaseClient.sql("""
                UPDATE result.grade_configs
                SET config = :config::jsonb, updated_at = NOW()
                WHERE school_id = :schoolId
                """)
                .bind("config", "[{\"grade\":\"A\"},{\"grade\":\"B\"},{\"grade\":\"C\"}]")
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();

        // Verify updated state
        StepVerifier.create(resultService.getGradingRules())
                .assertNext(response -> {
                    assertThat(response.gradesCount()).isEqualTo(3);
                    assertThat(response.message()).isEqualTo("Current grading rules");
                })
                .verifyComplete();

        // Verify only 1 config row exists (updated, not duplicated)
        assertThat(countRows(
                "SELECT COUNT(*) FROM result.grade_configs WHERE school_id = :schoolId",
                Map.of("schoolId", SCHOOL_ID))).isEqualTo(1L);
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
    }

    @DisplayName("Should add teacher and principal comments for student term")
    void shouldAddTeacherAndPrincipalCommentsForStudentTerm() {
        seedScoreEntryFixture();

        ReportCommentResponse teacherComment = resultService
                .addTeacherComment(STUDENT_ID, TERM_ID, "Consistent improvement")
                .block();
        ReportCommentResponse principalComment = resultService
                .addPrincipalComment(STUDENT_ID, TERM_ID, "Promoted")
                .block();

        assertThat(teacherComment).isNotNull();
        assertThat(teacherComment.comment()).isEqualTo("Consistent improvement");
        assertThat(principalComment).isNotNull();
        assertThat(principalComment.comment()).isEqualTo("Promoted");

        Map<String, Object> row = fetchOne(
                """
                SELECT teacher_comment, principal_comment
                FROM result.report_comments
                WHERE school_id = :schoolId AND student_id = :studentId AND term_id = :termId
                """,
                Map.of("schoolId", SCHOOL_ID, "studentId", STUDENT_ID, "termId", TERM_ID));
        assertThat(row.get("teacher_comment")).isEqualTo("Consistent improvement");
        assertThat(row.get("principal_comment")).isEqualTo("Promoted");
    }

    @Test
    @DisplayName("Should reject adding comment when student is not found in school")
    void shouldRejectAddingCommentWhenStudentNotFoundInSchool() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");
        seedSession();
        seedTerm();

        StepVerifier.create(resultService.addTeacherComment(UUID.randomUUID(), TERM_ID, "Any"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("STUDENT_NOT_FOUND");
                })
                .verify();
    }

    private CaConfigRequest validRequest() {
        return new CaConfigRequest(
                List.of(
                        new CaConfigRequest.CaComponentRequest("First Test", 20, 20, 1),
                        new CaConfigRequest.CaComponentRequest("Second Test", 20, 20, 2)),
                60);
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

    private void seedScoreEntryFixture() {
        seedSchool();
        seedUser("SCHOOL_ADMIN");
        seedSession();
        seedTerm();
        seedClass();
        seedStudent();
        seedSubject();
        seedClassSubject();
    }

    private void seedSchool() {
        databaseClient.sql("""
                INSERT INTO school.schools (
                    id, name, code, email, phone, address, city, state, country,
                    payment_config, sms_config, term_config, is_active
                )
                VALUES (
                    :schoolId, 'Grace International School', 'GIS',
                    'hello@gis.edu', '+2348012345678', '12 School Road',
                    'Lagos', 'Lagos', 'Nigeria',
                    '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, true
                )
                """)
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedUser(String userType) {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id, keycloak_id, school_id, email, phone, first_name, last_name, user_type, is_active
                )
                VALUES (
                    :userId, :keycloakId, :schoolId, :email, '+2348012345678',
                    'Result', 'User', :userType, true
                )
                """)
                .bind("userId", USER_ID)
                .bind("keycloakId", USER_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("email", "result-user@gis.edu")
                .bind("userType", userType)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedCaComponent(
            String name, int maxScore, BigDecimal weightPercentage, int sortOrder, boolean active) {
        UUID componentId = UUID.randomUUID();
        seedCaComponentWithId(componentId, name, maxScore, weightPercentage, sortOrder, active);
        return componentId;
    }

    private void seedCaComponentWithId(
            UUID componentId, String name, int maxScore, BigDecimal weightPercentage, int sortOrder, boolean active) {
        databaseClient.sql("""
                INSERT INTO result.ca_components (
                    id, school_id, name, max_score, weight_percentage, sort_order, is_active
                )
                VALUES (
                    :componentId, :schoolId, :name, :maxScore, :weightPercentage, :sortOrder, :active
                )
                """)
                .bind("componentId", componentId)
                .bind("schoolId", SCHOOL_ID)
                .bind("name", name)
                .bind("maxScore", maxScore)
                .bind("weightPercentage", weightPercentage)
                .bind("sortOrder", sortOrder)
                .bind("active", active)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedCaScoreFixture(UUID componentId) {
        seedSession();
        seedTerm();
        seedClass();
        UUID studentId = seedStudent();
        seedSubject();

        databaseClient.sql("""
                INSERT INTO result.ca_scores (
                    id, school_id, student_id, subject_id, class_id, term_id,
                    ca_component_id, score, max_score
                )
                VALUES (
                    :scoreId, :schoolId, :studentId, :subjectId, :classId, :termId,
                    :componentId, 10.0, 20
                )
                """)
                .bind("scoreId", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", studentId)
                .bind("subjectId", SUBJECT_ID)
                .bind("classId", CLASS_ID)
                .bind("termId", TERM_ID)
                .bind("componentId", componentId)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedSession() {
        databaseClient.sql("""
                INSERT INTO school.academic_sessions (
                    id, school_id, name, start_date, end_date, is_current
                )
                VALUES (
                    :sessionId, :schoolId, '2025/2026', :startDate, :endDate, true
                )
                """)
                .bind("sessionId", SESSION_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("startDate", LocalDate.parse("2025-09-01"))
                .bind("endDate", LocalDate.parse("2026-07-31"))
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedTerm() {
        databaseClient.sql("""
                INSERT INTO school.terms (
                    id, session_id, name, term_number, start_date, end_date, is_current
                )
                VALUES (
                    :termId, :sessionId, 'First Term', 1, :startDate, :endDate, true
                )
                """)
                .bind("termId", TERM_ID)
                .bind("sessionId", SESSION_ID)
                .bind("startDate", LocalDate.parse("2026-01-10"))
                .bind("endDate", LocalDate.parse("2026-04-10"))
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedClass() {
        databaseClient.sql("""
                INSERT INTO school.classes (
                    id, school_id, name, grade_level, academic_session_id, capacity, is_active
                )
                VALUES (
                    :classId, :schoolId, 'Basic 1A', 'Basic 1', :sessionId, 40, true
                )
                """)
                .bind("classId", CLASS_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("sessionId", SESSION_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedStudent() {
        databaseClient.sql("""
                INSERT INTO school.students (
                    id, school_id, admission_number, first_name, last_name,
                    current_class_id, enrollment_date, enrollment_status
                )
                VALUES (
                    :studentId, :schoolId, 'GIS-001', 'Test', 'Student',
                    :classId, :enrollmentDate, 'ACTIVE'
                )
                """)
                .bind("studentId", STUDENT_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("classId", CLASS_ID)
                .bind("enrollmentDate", LocalDate.parse("2025-09-01"))
                .fetch()
                .rowsUpdated()
                .block();
        return STUDENT_ID;
    }

    private void seedSubject() {
        databaseClient.sql("""
                INSERT INTO result.subjects (
                    id, school_id, name, code, category, is_active
                )
                VALUES (
                    :subjectId, :schoolId, 'Mathematics', 'MTH', 'SCIENCE', true
                )
                """)
                .bind("subjectId", SUBJECT_ID)
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedClassSubject() {
        databaseClient.sql("""
                INSERT INTO result.class_subjects (
                    id, school_id, class_id, subject_id, is_active
                )
                VALUES (
                    :id, :schoolId, :classId, :subjectId, true
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("classId", CLASS_ID)
                .bind("subjectId", SUBJECT_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedExam() {
        databaseClient.sql("""
                INSERT INTO result.exams (
                    id, school_id, term_id, name, exam_type, exam_date,
                    max_score, weight_percentage, is_published, created_by
                )
                VALUES (
                    :examId, :schoolId, :termId, 'End of Term', 'END_OF_TERM', :examDate,
                    100, 60.00, false, :userId
                )
                """)
                .bind("examId", EXAM_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("termId", TERM_ID)
                .bind("examDate", LocalDate.parse("2026-04-01"))
                .bind("userId", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedExamScore(BigDecimal score) {
        databaseClient.sql("""
                INSERT INTO result.scores (
                    id, school_id, exam_id, student_id, subject_id, class_id,
                    term_id, score, max_score, recorded_by
                )
                VALUES (
                    :scoreId, :schoolId, :examId, :studentId, :subjectId, :classId,
                    :termId, :score, 100, :userId
                )
                """)
                .bind("scoreId", SCORE_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("examId", EXAM_ID)
                .bind("studentId", STUDENT_ID)
                .bind("subjectId", SUBJECT_ID)
                .bind("classId", CLASS_ID)
                .bind("termId", TERM_ID)
                .bind("score", score)
                .bind("userId", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedCaScore(UUID componentId, BigDecimal score, int maxScore) {
        databaseClient.sql("""
                INSERT INTO result.ca_scores (
                    id, school_id, student_id, subject_id, class_id, term_id,
                    ca_component_id, score, max_score, recorded_by
                )
                VALUES (
                    :scoreId, :schoolId, :studentId, :subjectId, :classId, :termId,
                    :componentId, :score, :maxScore, :userId
                )
                """)
                .bind("scoreId", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", STUDENT_ID)
                .bind("subjectId", SUBJECT_ID)
                .bind("classId", CLASS_ID)
                .bind("termId", TERM_ID)
                .bind("componentId", componentId)
                .bind("score", score)
                .bind("maxScore", maxScore)
                .bind("userId", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedFinalScore() {
        databaseClient.sql("""
                INSERT INTO result.final_scores (
                    id, school_id, student_id, subject_id, class_id, term_id,
                    ca_total, ca_max_total, exam_score, exam_max_score,
                    final_score, grade, remark, points, subject_position
                )
                VALUES (
                    :id, :schoolId, :studentId, :subjectId, :classId, :termId,
                    18.0, 20, 70.0, 100, 78.0, 'B2', 'Very Good', 3.5, 1
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", STUDENT_ID)
                .bind("subjectId", SUBJECT_ID)
                .bind("classId", CLASS_ID)
                .bind("termId", TERM_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedRanking() {
        databaseClient.sql("""
                INSERT INTO result.class_rankings (
                    id, school_id, student_id, class_id, term_id,
                    total_score, total_max_score, average_percentage,
                    overall_grade, class_position, out_of, subjects_taken, subjects_passed
                )
                VALUES (
                    :id, :schoolId, :studentId, :classId, :termId,
                    78.0, 100, 78.0, 'B2', 1, 1, 1, 1
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", STUDENT_ID)
                .bind("classId", CLASS_ID)
                .bind("termId", TERM_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedReportComment() {
        databaseClient.sql("""
                INSERT INTO result.report_comments (
                    id, school_id, student_id, term_id,
                    teacher_comment, principal_comment,
                    attendance_days_open, attendance_days_present, attendance_days_absent
                )
                VALUES (
                    :id, :schoolId, :studentId, :termId,
                    'Doing well', 'Keep it up', 60, 58, 2
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", STUDENT_ID)
                .bind("termId", TERM_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedGuardianLink(boolean canViewResults) {
        databaseClient.sql("""
                INSERT INTO school.student_guardians (
                    id, school_id, first_name, last_name, phone, user_id, is_active
                )
                VALUES (
                    :guardianId, :schoolId, 'Parent', 'User', '+2348099999999', :userId, true
                )
                """)
                .bind("guardianId", GUARDIAN_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("userId", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();

        databaseClient.sql("""
                INSERT INTO school.student_guardian_links (
                    id, guardian_id, student_id, school_id, relationship, can_view_results
                )
                VALUES (
                    :id, :guardianId, :studentId, :schoolId, 'FATHER', :canViewResults
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("guardianId", GUARDIAN_ID)
                .bind("studentId", STUDENT_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("canViewResults", canViewResults)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedPublishedResult() {
        databaseClient.sql("""
                INSERT INTO result.published_results (
                    id, school_id, term_id, published_by
                )
                VALUES (
                    :id, :schoolId, :termId, :userId
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("termId", TERM_ID)
                .bind("userId", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedGradeConfig(String configJson) {
        databaseClient.sql("""
                INSERT INTO result.grade_configs (
                    id, school_id, config, is_active
                )
                VALUES (
                    :id, :schoolId, :config::jsonb, true
                )
                """)
                .bind("id", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("config", configJson)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private long countRows(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return ((Number) spec.fetch().one().block().get("count")).longValue();
    }

    private Map<String, Object> fetchOne(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return spec.fetch().one().block();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM result.score_audit_log").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.published_results").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.report_comments").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.class_rankings").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.final_scores").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.scores").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.ca_scores").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.exams").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.class_subjects").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.subjects").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.ca_components").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.grade_configs").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_guardian_links").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_class_history").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.students").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_guardians").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM auth.user_school_roles").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM auth.users").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.terms").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.academic_sessions").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .email("admin@gis.edu")
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }

    private SchoolFeeUser parentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .email("parent@gis.edu")
                .userType("PARENT")
                .roles(Set.of("PARENT"))
                .build();
    }
}
