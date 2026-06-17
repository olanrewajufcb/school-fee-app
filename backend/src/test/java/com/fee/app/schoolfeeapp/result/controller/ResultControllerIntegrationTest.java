package com.fee.app.schoolfeeapp.result.controller;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class ResultControllerIntegrationTest {

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
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

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
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Test
    @DisplayName("Should configure CA through controller")
    void shouldConfigureCaThroughController() {
        seedSchool();

        authenticatedClient("SCHOOL_ADMIN", "SCHOOL_ADMIN")
                .put()
                .uri("/api/v1/results/ca-config")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequestBody())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.componentCount").isEqualTo(2)
                .jsonPath("$.data.examWeightPercentage").isEqualTo(60.0)
                .jsonPath("$.data.message").isEqualTo("CA configuration updated");

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.ca_components
                WHERE school_id = :schoolId AND is_active = true
                """, Map.of("schoolId", SCHOOL_ID))).isEqualTo(2);
    }

    @Test
    @DisplayName("Should get CA config through controller")
    void shouldGetCaConfigThroughController() {
        seedSchool();
        seedCaComponent("First Test", 20, BigDecimal.valueOf(20), 1, true);
        seedCaComponent("Second Test", 20, BigDecimal.valueOf(20), 2, true);
        seedCaComponent("Old Test", 20, BigDecimal.valueOf(30), 3, false);

        authenticatedClient("TEACHER", "TEACHER")
                .get()
                .uri("/api/v1/results/ca-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.componentCount").isEqualTo(2)
                .jsonPath("$.data.examWeightPercentage").isEqualTo(60.0)
                .jsonPath("$.data.message").isEqualTo("Current CA configuration");
    }

    @Test
    @DisplayName("Should reject invalid CA weights through controller")
    void shouldRejectInvalidCaWeightsThroughController() {
        seedSchool();
        Map<String, Object> request = Map.of(
                "components", List.of(Map.of(
                        "name", "First Test",
                        "maxScore", 20,
                        "weightPercentage", 20,
                        "sortOrder", 1)),
                "examWeightPercentage", 50);

        authenticatedClient("SCHOOL_ADMIN", "SCHOOL_ADMIN")
                .put()
                .uri("/api/v1/results/ca-config")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errors[0].code").isEqualTo("INVALID_WEIGHTS");
    }

    @Test
    @DisplayName("Should forbid accountant from configuring CA")
    void shouldForbidAccountantFromConfiguringCa() {
        seedSchool();

        authenticatedClient("ACCOUNTANT", "ACCOUNTANT")
                .put()
                .uri("/api/v1/results/ca-config")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequestBody())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Should enter CA scores through controller")
    void shouldEnterCaScoresThroughController() {
        seedScoreEntryFixture();
        seedCaComponentWithId(COMPONENT_ID, "First Test", 20, BigDecimal.valueOf(20), 1, true);

        authenticatedClient("TEACHER", "TEACHER")
                .post()
                .uri("/api/v1/results/ca-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(caScoreRequestBody())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.scoresEntered").isEqualTo(1)
                .jsonPath("$.data.message").isEqualTo("CA scores recorded");

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.ca_scores
                WHERE student_id = :studentId AND ca_component_id = :componentId
                """, Map.of("studentId", STUDENT_ID, "componentId", COMPONENT_ID))).isEqualTo(1);
    }

    @Test
    @DisplayName("Should enter exam scores through controller")
    void shouldEnterExamScoresThroughController() {
        seedScoreEntryFixture();
        seedExam();

        authenticatedClient("TEACHER", "TEACHER")
                .post()
                .uri("/api/v1/results/exam-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(examScoreRequestBody())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.scoresEntered").isEqualTo(1)
                .jsonPath("$.data.finalScoresComputed").isEqualTo(1);

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.final_scores
                WHERE student_id = :studentId AND term_id = :termId
                """, Map.of("studentId", STUDENT_ID, "termId", TERM_ID))).isEqualTo(1);
    }

    @Test
    @DisplayName("Should update score through controller")
    void shouldUpdateScoreThroughController() {
        seedScoreEntryFixture();
        seedExam();
        seedExamScore(BigDecimal.valueOf(55));

        authenticatedClient("TEACHER", "TEACHER")
                .put()
                .uri("/api/v1/results/scores/{scoreId}", SCORE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("score", 70, "reason", "Correction"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.scoreId").isEqualTo(SCORE_ID.toString())
                .jsonPath("$.data.newScore").isEqualTo(70.0)
                .jsonPath("$.data.previousScore").isEqualTo(55.0);

        assertThat(countRows("""
                SELECT COUNT(*) AS count
                FROM result.score_audit_log
                WHERE score_id = :scoreId
                """, Map.of("scoreId", SCORE_ID))).isEqualTo(1);
    }

    @Test
    @DisplayName("Should get student result through controller for parent")
    void shouldGetStudentResultThroughControllerForParent() {
        seedScoreEntryFixture();
        seedCaComponentWithId(COMPONENT_ID, "First Test", 20, BigDecimal.valueOf(20), 1, true);
        seedCaScore(COMPONENT_ID, BigDecimal.valueOf(18), 20);
        seedFinalScore();
        seedRanking();
        seedReportComment();
        seedGuardianLink(true);
        seedPublishedResult();

        authenticatedClient("PARENT", "PARENT")
                .get()
                .uri("/api/v1/results/students/{studentId}/term/{termId}", STUDENT_ID, TERM_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.student.studentId").isEqualTo(STUDENT_ID.toString())
                .jsonPath("$.data.student.fullName").isEqualTo("Test Student")
                .jsonPath("$.data.subjects[0].subjectName").isEqualTo("Mathematics")
                .jsonPath("$.data.subjects[0].caScores[0].component").isEqualTo("First Test")
                .jsonPath("$.data.teacherComment").isEqualTo("Doing well");
    }

    @Test
    @DisplayName("Should reject parent student result before publication through controller")
    void shouldRejectParentStudentResultBeforePublicationThroughController() {
        seedScoreEntryFixture();
        seedGuardianLink(true);

        authenticatedClient("PARENT", "PARENT")
                .get()
                .uri("/api/v1/results/students/{studentId}/term/{termId}", STUDENT_ID, TERM_ID)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errors[0].code").isEqualTo("RESULTS_NOT_PUBLISHED");
    }

    private WebTestClient authenticatedClient(String userType, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID.toString())
                        .claim("preferred_username", "result-admin")
                        .claim("email", "result-admin@gis.edu")
                        .claim("given_name", "Result")
                        .claim("family_name", "Admin")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", SCHOOL_ID.toString())
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private Map<String, Object> validRequestBody() {
        return Map.of(
                "components", List.of(
                        Map.of(
                                "name", "First Test",
                                "maxScore", 20,
                                "weightPercentage", 20,
                                "sortOrder", 1),
                        Map.of(
                                "name", "Second Test",
                                "maxScore", 20,
                                "weightPercentage", 20,
                                "sortOrder", 2)),
                "examWeightPercentage", 60);
    }

    private Map<String, Object> caScoreRequestBody() {
        return Map.of(
                "termId", TERM_ID.toString(),
                "classId", CLASS_ID.toString(),
                "subjectId", SUBJECT_ID.toString(),
                "caComponentId", COMPONENT_ID.toString(),
                "maxScore", 20,
                "scores", List.of(Map.of(
                        "studentId", STUDENT_ID.toString(),
                        "score", 15)));
    }

    private Map<String, Object> examScoreRequestBody() {
        return Map.of(
                "examId", EXAM_ID.toString(),
                "classId", CLASS_ID.toString(),
                "subjectId", SUBJECT_ID.toString(),
                "termId", TERM_ID.toString(),
                "maxScore", 100,
                "scores", List.of(Map.of(
                        "studentId", STUDENT_ID.toString(),
                        "score", 75)));
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

    private void seedCaComponent(
            String name, int maxScore, BigDecimal weightPercentage, int sortOrder, boolean active) {
        seedCaComponentWithId(UUID.randomUUID(), name, maxScore, weightPercentage, sortOrder, active);
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

    private void seedStudent() {
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

    private long countRows(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return ((Number) spec.fetch().one().block().get("count")).longValue();
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
}
