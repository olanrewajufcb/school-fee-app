package com.fee.app.schoolfeeapp.school.controller;

import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.dto.request.CloseSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.CreateAcademicSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.CreateSchoolRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSchoolRequest;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class SchoolControllerIntegrationTest {

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

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private AcademicSessionRepository sessionRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID ADMIN_KEYCLOAK_ID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Nested
    @DisplayName("POST /api/v1/schools")
    class CreateSchoolIntegrationTests {

        @Test
        @DisplayName("Should create school, default session, terms, and outbox event")
        void shouldCreateSchoolSessionTermsAndOutboxEvent() {
            CreateSchoolRequest request = validCreateSchoolRequest("GIS");
            when(keycloakAdminService.createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name())))
                    .thenReturn(Mono.just(new KeycloakUserResult(ADMIN_KEYCLOAK_ID, "tempPassword")));

            superAdminClient()
                    .post()
                    .uri("/api/v1/schools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.schoolId").exists()
                    .jsonPath("$.data.name").isEqualTo("Grace International School")
                    .jsonPath("$.data.code").isEqualTo("GIS")
                    .jsonPath("$.data.status").isEqualTo("ACTIVE")
                    .jsonPath("$.data.adminUserCreated").isEqualTo(true)
                    .jsonPath("$.data.adminTemporaryPassword").isEqualTo("Sent via email")
                    .jsonPath("$.data.currentSessionId").exists()
                    .jsonPath("$.data.currentSessionName").exists()
                    .jsonPath("$.timestamp").exists();

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.schools WHERE code = 'GIS'"))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions s
                    JOIN school.schools sc ON sc.id = s.school_id
                    WHERE sc.code = 'GIS' AND s.is_current = true
                    """))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms t
                    JOIN school.academic_sessions s ON s.id = t.session_id
                    JOIN school.schools sc ON sc.id = s.school_id
                    WHERE sc.code = 'GIS'
                    """))
                    .isEqualTo(3);

            Map<String, Object> outboxEvent = fetchOne("""
                    SELECT aggregate_type, event_type, payload ->> 'schoolCode' AS school_code,
                           payload ->> 'adminKeycloakId' AS admin_keycloak_id
                    FROM outbox.outbox_events
                    WHERE event_type = 'SCHOOL_CREATED'
                    """);
            assertThat(outboxEvent)
                    .containsEntry("aggregate_type", "SCHOOL")
                    .containsEntry("event_type", "SCHOOL_CREATED")
                    .containsEntry("school_code", "GIS")
                    .containsEntry("admin_keycloak_id", ADMIN_KEYCLOAK_ID.toString());

            verify(keycloakAdminService)
                    .createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name()));
        }

        @Test
        @DisplayName("Should create school with manual admin setup when identity provider fails")
        void shouldCreateSchoolWithManualAdminSetupWhenIdentityProviderFails() {
            CreateSchoolRequest request = validCreateSchoolRequest("GMS");
            when(keycloakAdminService.createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name())))
                    .thenReturn(Mono.error(new RuntimeException("Keycloak unavailable")));

            superAdminClient()
                    .post()
                    .uri("/api/v1/schools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.code").isEqualTo("GMS")
                    .jsonPath("$.data.adminUserCreated").isEqualTo(false)
                    .jsonPath("$.data.adminTemporaryPassword").isEqualTo("Manual setup required")
                    .jsonPath("$.data.message").value(message ->
                            assertThat(message.toString()).contains("admin account setup failed"));

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.schools WHERE code = 'GMS'"))
                    .isEqualTo(1);
            assertThat(fetchOne("""
                    SELECT payload ->> 'adminKeycloakId' AS admin_keycloak_id
                    FROM outbox.outbox_events
                    WHERE event_type = 'SCHOOL_CREATED'
                    """))
                    .containsEntry("admin_keycloak_id", null);
        }

        @Test
        @DisplayName("Should return conflict for duplicate school code")
        void shouldReturnConflictForDuplicateSchoolCode() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS");

            superAdminClient()
                    .post()
                    .uri("/api/v1/schools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validCreateSchoolRequest("GIS"))
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_RESOURCE")
                    .jsonPath("$.errors[0].field").isEqualTo("code");

            verify(keycloakAdminService, never())
                    .createStaffUser(any(CreateStaffRequest.class), any(UUID.class), any());
        }

        @Test
        @DisplayName("Should validate create school request")
        void shouldValidateCreateSchoolRequest() {
            CreateSchoolRequest invalidRequest = new CreateSchoolRequest(
                    "Gi",
                    "gis",
                    "not-an-email",
                    "",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    new CreateSchoolRequest.AdminUser("", "", "", ""));

            superAdminClient()
                    .post()
                    .uri("/api/v1/schools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(invalidRequest)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors.length()").value(length ->
                            assertThat(((Number) length).intValue()).isGreaterThanOrEqualTo(5));
        }

        @Test
        @DisplayName("Should reject create school without authentication")
        void shouldRejectCreateSchoolWithoutAuthentication() {
            webTestClient
                    .post()
                    .uri("/api/v1/schools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validCreateSchoolRequest("GIS"))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject create school without super admin role")
        void shouldRejectCreateSchoolWithoutSuperAdminRole() {
            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validCreateSchoolRequest("GIS"))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET school endpoints")
    class GetSchoolIntegrationTests {

        @Test
        @DisplayName("Should return current school for authenticated school user")
        void shouldReturnCurrentSchoolForAuthenticatedSchoolUser() {
            seedSchoolWithCurrentTerm();

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.schoolId").isEqualTo(SCHOOL_ID.toString())
                    .jsonPath("$.data.code").isEqualTo("GIS")
                    .jsonPath("$.data.status").isEqualTo("ACTIVE")
                    .jsonPath("$.data.currentTerm.name").isEqualTo("First Term")
                    .jsonPath("$.data.currentTerm.sessionName").isEqualTo("2025/2026 Academic Year")
                    .jsonPath("$.data.paymentConfig.paystackPublicKey").isEqualTo("123456");
        }

        @Test
        @DisplayName("Should return bad request when current school is not found")
        void shouldReturnBadRequestWhenCurrentSchoolIsNotFound() {
            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return school by id for super admin")
        void shouldReturnSchoolByIdForSuperAdmin() {
            seedSchoolWithCurrentTerm();

            superAdminClient()
                    .get()
                    .uri("/api/v1/schools/{schoolId}", SCHOOL_ID)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.schoolId").isEqualTo(SCHOOL_ID.toString())
                    .jsonPath("$.data.currentTerm.termId").exists();
        }

        @Test
        @DisplayName("Should reject school by id for non-super-admin user")
        void shouldRejectSchoolByIdForNonSuperAdminUser() {
            seedSchoolWithCurrentTerm();

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/{schoolId}", SCHOOL_ID)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Should reject current school without authentication")
        void shouldRejectCurrentSchoolWithoutAuthentication() {
            webTestClient
                    .get()
                    .uri("/api/v1/schools/current")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should list schools for super admin")
        void shouldListSchoolsForSuperAdmin() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Inactive School", "INS", false);

            byte[] responseBody = superAdminClient()
                    .get()
                    .uri("/api/v1/schools?status=ALL&page=0&size=10")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.page").isEqualTo(0)
                    .jsonPath("$.data.size").isEqualTo(10)
                    .jsonPath("$.data.totalElements").isEqualTo(2)
                    .jsonPath("$.data.totalPages").isEqualTo(1)
                    .jsonPath("$.data.content.length()").isEqualTo(2)
                    .returnResult()
                    .getResponseBody();

            JsonNode content = objectMapper.readTree(responseBody)
                    .path("data")
                    .path("content");
            Map<String, String> statusByCode = new HashMap<>();
            content.forEach(school -> statusByCode.put(
                    school.path("code").asText(),
                    school.path("status").asText()));
            assertThat(statusByCode)
                    .containsEntry("GIS", "ACTIVE")
                    .containsEntry("INS", "INACTIVE");
        }

        @Test
        @DisplayName("Should return bad request for invalid school list status")
        void shouldReturnBadRequestForInvalidSchoolListStatus() {
            superAdminClient()
                    .get()
                    .uri("/api/v1/schools?status=ARCHIVED")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_STATUS")
                    .jsonPath("$.errors[0].field").isEqualTo("status");
        }

        @Test
        @DisplayName("Should reject school list for non-super-admin user")
        void shouldRejectSchoolListForNonSuperAdminUser() {
            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/schools/current/sessions")
    class GetSessionsIntegrationTests {

        @Test
        @DisplayName("Should return current school sessions ordered with ordered terms")
        void shouldReturnCurrentSchoolSessionsOrderedWithOrderedTerms() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSessionWithTerms(
                    SCHOOL_ID,
                    "2026/2027 Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    false,
                    List.of(
                            new TermSeed("Second Term", 2, false),
                            new TermSeed("First Term", 1, true)));
            seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current/sessions")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(2)
                    .jsonPath("$.data[0].name").isEqualTo("2026/2027 Academic Year")
                    .jsonPath("$.data[0].isCurrent").isEqualTo(false)
                    .jsonPath("$.data[0].terms.length()").isEqualTo(2)
                    .jsonPath("$.data[0].terms[0].name").isEqualTo("First Term")
                    .jsonPath("$.data[0].terms[0].termNumber").isEqualTo(1)
                    .jsonPath("$.data[0].terms[0].isCurrent").isEqualTo(true)
                    .jsonPath("$.data[0].terms[1].name").isEqualTo("Second Term")
                    .jsonPath("$.data[0].terms[1].termNumber").isEqualTo(2)
                    .jsonPath("$.data[0].terms[1].isCurrent").isEqualTo(false)
                    .jsonPath("$.data[1].name").isEqualTo("2025/2026 Academic Year")
                    .jsonPath("$.data[1].isCurrent").isEqualTo(true);
        }

        @Test
        @DisplayName("Should return empty sessions list")
        void shouldReturnEmptySessionsList() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);

            schoolAdminClient(SCHOOL_ID)
                    .get()
                    .uri("/api/v1/schools/current/sessions")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("Should reject sessions without authentication")
        void shouldRejectSessionsWithoutAuthentication() {
            webTestClient
                    .get()
                    .uri("/api/v1/schools/current/sessions")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject sessions for parent role")
        void shouldRejectSessionsForParentRole() {
            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .get()
                    .uri("/api/v1/schools/current/sessions")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/schools/current/sessions")
    class CreateSessionIntegrationTests {

        @Test
        @DisplayName("Should create current session for school admin")
        void shouldCreateCurrentSessionForSchoolAdmin() {
            seedSchoolWithCurrentTerm();

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validCreateAcademicSessionRequest(true))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.name").isEqualTo("2026/2027 Academic Year")
                    .jsonPath("$.data.isCurrent").isEqualTo(true)
                    .jsonPath("$.data.terms.length()").isEqualTo(2)
                    .jsonPath("$.data.terms[0].name").isEqualTo("First Term")
                    .jsonPath("$.data.terms[0].termNumber").isEqualTo(1)
                    .jsonPath("$.data.terms[0].isCurrent").isEqualTo(true)
                    .jsonPath("$.data.terms[1].name").isEqualTo("Second Term")
                    .jsonPath("$.data.terms[1].termNumber").isEqualTo(2)
                    .jsonPath("$.data.terms[1].isCurrent").isEqualTo(false);

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId AND is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(fetchOne("""
                    SELECT t.name AS term_name, s.name AS session_name
                    FROM school.terms t
                    JOIN school.academic_sessions s ON s.id = t.session_id
                    WHERE s.school_id = :schoolId AND t.is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("term_name", "First Term")
                    .containsEntry("session_name", "2026/2027 Academic Year");
        }

        @Test
        @DisplayName("Should return bad request for overlapping session terms")
        void shouldReturnBadRequestForOverlappingSessionTerms() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);

            CreateAcademicSessionRequest request = new CreateAcademicSessionRequest(
                    "2026/2027 Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    List.of(
                            new CreateAcademicSessionRequest.TermRequest(
                                    "First Term",
                                    1,
                                    LocalDate.of(2026, 9, 8),
                                    LocalDate.of(2026, 12, 19)),
                            new CreateAcademicSessionRequest.TermRequest(
                                    "Second Term",
                                    2,
                                    LocalDate.of(2026, 12, 15),
                                    LocalDate.of(2027, 4, 4))),
                    false);

            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_TERM_CONFIG")
                    .jsonPath("$.errors[0].field").isEqualTo("terms");

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isZero();
        }

        @Test
        @DisplayName("Should validate create session request body")
        void shouldValidateCreateSessionRequestBody() {
            schoolAdminClient(SCHOOL_ID)
                    .post()
                    .uri("/api/v1/schools/current/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "name": "",
                              "startDate": null,
                              "endDate": null,
                              "terms": [],
                              "setAsCurrent": false
                            }
                            """)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors.length()").value(length ->
                            assertThat(((Number) length).intValue()).isGreaterThanOrEqualTo(4));
        }

        @Test
        @DisplayName("Should reject create session without authentication")
        void shouldRejectCreateSessionWithoutAuthentication() {
            webTestClient
                    .post()
                    .uri("/api/v1/schools/current/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validCreateAcademicSessionRequest(false))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject create session for accountant role")
        void shouldRejectCreateSessionForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/schools/current/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validCreateAcademicSessionRequest(false))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/schools/current/sessions/{sessionId}/set-current")
    class SetCurrentSessionIntegrationTests {

        @Test
        @DisplayName("Should set current session for school admin")
        void shouldSetCurrentSessionForSchoolAdmin() {
            seedSchoolWithCurrentTerm();
            AcademicSession targetSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2026/2027 Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    false,
                    List.of(
                            new TermSeed("Second Term", 2, false),
                            new TermSeed("First Term", 1, false)));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/set-current", targetSession.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.sessionId").isEqualTo(targetSession.getId().toString())
                    .jsonPath("$.data.name").isEqualTo("2026/2027 Academic Year")
                    .jsonPath("$.data.isCurrent").isEqualTo(true)
                    .jsonPath("$.data.terms.length()").isEqualTo(2)
                    .jsonPath("$.data.terms[0].name").isEqualTo("First Term")
                    .jsonPath("$.data.terms[0].termNumber").isEqualTo(1)
                    .jsonPath("$.data.terms[0].isCurrent").isEqualTo(true)
                    .jsonPath("$.data.terms[1].name").isEqualTo("Second Term")
                    .jsonPath("$.data.terms[1].termNumber").isEqualTo(2)
                    .jsonPath("$.data.terms[1].isCurrent").isEqualTo(false);

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId AND is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(fetchOne("""
                    SELECT t.name AS term_name, s.name AS session_name
                    FROM school.terms t
                    JOIN school.academic_sessions s ON s.id = t.session_id
                    WHERE s.school_id = :schoolId AND t.is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("term_name", "First Term")
                    .containsEntry("session_name", "2026/2027 Academic Year");
        }

        @Test
        @DisplayName("Should return bad request for missing session")
        void shouldReturnBadRequestForMissingSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            UUID missingSessionId = UUID.randomUUID();

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/set-current", missingSessionId)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return bad request for session in another school")
        void shouldReturnBadRequestForSessionInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSchoolSession = seedSessionWithTerms(
                    OTHER_SCHOOL_ID,
                    "Other Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, false)));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/set-current", otherSchoolSession.getId())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_NOT_IN_SCHOOL");
        }

        @Test
        @DisplayName("Should reject set current session without authentication")
        void shouldRejectSetCurrentSessionWithoutAuthentication() {
            webTestClient
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/set-current", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject set current session for accountant role")
        void shouldRejectSetCurrentSessionForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/set-current", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/schools/current/sessions/current/terms/{termId}/set-current")
    class SetCurrentTermIntegrationTests {

        @Test
        @DisplayName("Should set current term for school admin")
        void shouldSetCurrentTermForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(
                            new TermSeed("First Term", 1, true),
                            new TermSeed("Second Term", 2, false)));
            UUID firstTermId = findTermId(session.getId(), "First Term");
            UUID secondTermId = findTermId(session.getId(), "Second Term");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/current/terms/{termId}/set-current", secondTermId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.termId").isEqualTo(secondTermId.toString())
                    .jsonPath("$.data.name").isEqualTo("Second Term")
                    .jsonPath("$.data.sessionName").isEqualTo("2025/2026 Academic Year")
                    .jsonPath("$.data.isCurrent").isEqualTo(true)
                    .jsonPath("$.data.previousCurrentTerm.termId").isEqualTo(firstTermId.toString())
                    .jsonPath("$.data.previousCurrentTerm.name").isEqualTo("First Term")
                    .jsonPath("$.data.previousCurrentTerm.status").isEqualTo("COMPLETED");

            assertThat(fetchOne("""
                    SELECT is_current, status, completed_by
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", firstTermId)))
                    .containsEntry("is_current", false)
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("completed_by", USER_ID);
            assertThat(fetchOne("""
                    SELECT is_current, status, completed_at, completed_by
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", secondTermId)))
                    .containsEntry("is_current", true)
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("completed_at", null)
                    .containsEntry("completed_by", null);
        }

        @Test
        @DisplayName("Should return bad request for missing term")
        void shouldReturnBadRequestForMissingTerm() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            UUID missingTermId = UUID.randomUUID();

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/current/terms/{termId}/set-current", missingTermId)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("TERM_NOT_FOUND");
        }

        @Test
        @DisplayName("Should return bad request for term outside current session")
        void shouldReturnBadRequestForTermOutsideCurrentSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));
            AcademicSession oldSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2024/2025 Academic Year",
                    LocalDate.of(2024, 9, 8),
                    LocalDate.of(2025, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, false)));
            UUID oldTermId = findTermId(oldSession.getId(), "First Term");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/current/terms/{termId}/set-current", oldTermId)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("TERM_NOT_IN_CURRENT_SESSION")
                    .jsonPath("$.errors[0].field").isEqualTo("termId");
        }

        @Test
        @DisplayName("Should return bad request for term in another school")
        void shouldReturnBadRequestForTermInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSchoolSession = seedSessionWithTerms(
                    OTHER_SCHOOL_ID,
                    "Other Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));
            UUID otherSchoolTermId = findTermId(otherSchoolSession.getId(), "First Term");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/current/terms/{termId}/set-current", otherSchoolTermId)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("TERM_NOT_IN_SCHOOL");
        }

        @Test
        @DisplayName("Should return bad request for term in completed current session")
        void shouldReturnBadRequestForTermInCompletedCurrentSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, false)));
            UUID termId = findTermId(session.getId(), "First Term");
            databaseClient.sql("""
                    UPDATE school.academic_sessions
                    SET status = 'COMPLETED',
                        closed_at = NOW(),
                        closed_by = :closedBy
                    WHERE id = :sessionId
                    """)
                    .bind("closedBy", USER_ID)
                    .bind("sessionId", session.getId())
                    .fetch()
                    .rowsUpdated()
                    .block();

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/current/terms/{termId}/set-current", termId)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_ALREADY_CLOSED");
        }

        @Test
        @DisplayName("Should reject set current term without authentication")
        void shouldRejectSetCurrentTermWithoutAuthentication() {
            webTestClient
                    .put()
                    .uri("/api/v1/schools/current/sessions/current/terms/{termId}/set-current", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject set current term for accountant role")
        void shouldRejectSetCurrentTermForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .put()
                    .uri("/api/v1/schools/current/sessions/current/terms/{termId}/set-current", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/schools/current/sessions/{sessionId}")
    class UpdateSessionIntegrationTests {

        @Test
        @DisplayName("Should update session and selected term for school admin")
        void shouldUpdateSessionAndSelectedTermForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(
                            new TermSeed("First Term", 1, true),
                            new TermSeed("Second Term", 2, false)));
            UUID firstTermId = findTermId(session.getId(), "First Term");

            UpdateSessionRequest request = new UpdateSessionRequest(
                    "2025/2026 Revised Academic Year",
                    LocalDate.of(2025, 9, 1),
                    null,
                    List.of(new UpdateSessionRequest.TermUpdate(
                            firstTermId,
                            "Opening Term",
                            LocalDate.of(2025, 9, 1),
                            LocalDate.of(2025, 10, 7))));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}", session.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.sessionId").isEqualTo(session.getId().toString())
                    .jsonPath("$.data.name").isEqualTo("2025/2026 Revised Academic Year")
                    .jsonPath("$.data.updatedAt").exists();

            assertThat(fetchOne("""
                    SELECT name, start_date, end_date
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", session.getId())))
                    .containsEntry("name", "2025/2026 Revised Academic Year")
                    .containsEntry("start_date", LocalDate.of(2025, 9, 1))
                    .containsEntry("end_date", LocalDate.of(2026, 9, 7));
            assertThat(fetchOne("""
                    SELECT name, start_date, end_date
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", firstTermId)))
                    .containsEntry("name", "Opening Term")
                    .containsEntry("start_date", LocalDate.of(2025, 9, 1))
                    .containsEntry("end_date", LocalDate.of(2025, 10, 7));
        }

        @Test
        @DisplayName("Should return bad request for empty update request")
        void shouldReturnBadRequestForEmptyUpdateRequest() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}", session.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_SESSION_UPDATE");
        }

        @Test
        @DisplayName("Should return bad request for overlapping term update")
        void shouldReturnBadRequestForOverlappingTermUpdate() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(
                            new TermSeed("First Term", 1, true),
                            new TermSeed("Second Term", 2, false)));
            UUID secondTermId = findTermId(session.getId(), "Second Term");

            UpdateSessionRequest request = new UpdateSessionRequest(
                    "Should Not Persist",
                    null,
                    null,
                    List.of(new UpdateSessionRequest.TermUpdate(
                            secondTermId,
                            "Overlapping Second Term",
                            LocalDate.of(2025, 10, 1),
                            null)));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}", session.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_TERM_CONFIG")
                    .jsonPath("$.errors[0].field").isEqualTo("terms");

            assertThat(fetchOne("""
                    SELECT name
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", session.getId())))
                    .containsEntry("name", "2025/2026 Academic Year");
        }

        @Test
        @DisplayName("Should return bad request for session in another school")
        void shouldReturnBadRequestForUpdateSessionInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSchoolSession = seedSessionWithTerms(
                    OTHER_SCHOOL_ID,
                    "Other Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, false)));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}", otherSchoolSession.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateSessionRequest("Nope", null, null, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_NOT_IN_SCHOOL");
        }

        @Test
        @DisplayName("Should reject update session without authentication")
        void shouldRejectUpdateSessionWithoutAuthentication() {
            webTestClient
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateSessionRequest("Revised", null, null, null))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject update session for accountant role")
        void shouldRejectUpdateSessionForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateSessionRequest("Revised", null, null, null))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/schools/current/sessions/{sessionId}/close")
    class CloseSessionIntegrationTests {

        @Test
        @DisplayName("Should close non-current session and complete terms for school admin")
        void shouldCloseNonCurrentSessionAndCompleteTermsForSchoolAdmin() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));
            AcademicSession closingSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2024/2025 Academic Year",
                    LocalDate.of(2024, 9, 8),
                    LocalDate.of(2025, 9, 7),
                    false,
                    List.of(
                            new TermSeed("First Term", 1, true),
                            new TermSeed("Second Term", 2, false)));

            CloseSessionRequest request = new CloseSessionRequest(
                    true,
                    true,
                    "  End of year reconciliation complete  ");

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/close", closingSession.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.sessionId").isEqualTo(closingSession.getId().toString())
                    .jsonPath("$.data.name").isEqualTo("2024/2025 Academic Year")
                    .jsonPath("$.data.status").isEqualTo("COMPLETED")
                    .jsonPath("$.data.closedAt").exists()
                    .jsonPath("$.data.termsCompleted").isEqualTo(2)
                    .jsonPath("$.data.studentsArchived").isEqualTo(0)
                    .jsonPath("$.data.message").value(message ->
                            assertThat(message.toString())
                                    .contains("Terms marked completed")
                                    .contains("Student archiving is not yet implemented"));

            assertThat(fetchOne("""
                    SELECT status, is_current, closed_at, closed_by, closed_notes
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", closingSession.getId())))
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("is_current", false)
                    .containsEntry("closed_by", USER_ID)
                    .containsEntry("closed_notes", "End of year reconciliation complete");
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms
                    WHERE session_id = :sessionId
                      AND status = 'COMPLETED'
                      AND completed_by = :completedBy
                      AND is_current = false
                    """, Map.of("sessionId", closingSession.getId(), "completedBy", USER_ID)))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Should return bad request when closing current session")
        void shouldReturnBadRequestWhenClosingCurrentSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession currentSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/close", currentSession.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CloseSessionRequest(true, false, "Close current"))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("CANNOT_CLOSE_CURRENT_SESSION")
                    .jsonPath("$.errors[0].field").isEqualTo("sessionId");

            assertThat(fetchOne("""
                    SELECT is_current, closed_at
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", currentSession.getId())))
                    .containsEntry("is_current", true)
                    .containsEntry("closed_at", null);
        }

        @Test
        @DisplayName("Should return bad request when closing already completed session")
        void shouldReturnBadRequestWhenClosingAlreadyCompletedSession() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            AcademicSession closingSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2024/2025 Academic Year",
                    LocalDate.of(2024, 9, 8),
                    LocalDate.of(2025, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, false)));
            databaseClient.sql("""
                    UPDATE school.academic_sessions
                    SET status = 'COMPLETED',
                        closed_at = NOW(),
                        closed_by = :closedBy
                    WHERE id = :sessionId
                    """)
                    .bind("closedBy", USER_ID)
                    .bind("sessionId", closingSession.getId())
                    .fetch()
                    .rowsUpdated()
                    .block();

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/close", closingSession.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CloseSessionRequest(true, false, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_ALREADY_CLOSED")
                    .jsonPath("$.errors[0].field").isEqualTo("sessionId");
        }

        @Test
        @DisplayName("Should return bad request when closing session in another school")
        void shouldReturnBadRequestWhenClosingSessionInAnotherSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSchoolSession = seedSessionWithTerms(
                    OTHER_SCHOOL_ID,
                    "Other Academic Year",
                    LocalDate.of(2024, 9, 8),
                    LocalDate.of(2025, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, false)));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/close", otherSchoolSession.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CloseSessionRequest(false, false, null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SESSION_NOT_IN_SCHOOL");
        }

        @Test
        @DisplayName("Should reject close session without authentication")
        void shouldRejectCloseSessionWithoutAuthentication() {
            webTestClient
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/close", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CloseSessionRequest(false, false, null))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject close session for accountant role")
        void shouldRejectCloseSessionForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .put()
                    .uri("/api/v1/schools/current/sessions/{sessionId}/close", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CloseSessionRequest(false, false, null))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("PUT school endpoints")
    class UpdateAndDeactivateIntegrationTests {

        @Test
        @DisplayName("Should update current school and persist JSON config changes")
        void shouldUpdateCurrentSchoolAndPersistJsonConfigChanges() {
            seedSchoolWithCurrentTerm();
            UpdateSchoolRequest request = new UpdateSchoolRequest(
                    "updated@gis.edu",
                    "+2348011111111",
                    "34 Updated Avenue",
                    "Ikeja",
                    "Lagos",
                    "https://cdn.example.com/new-logo.png",
                    new UpdateSchoolRequest.PaymentConfig(
                            "654321",
                            "NEWGIS",
                            List.of("BANK_TRANSFER", "CARD")),
                    new UpdateSchoolRequest.SmsConfig(
                            "AFRICASTALKING",
                            "secret-key",
                            "gis-user",
                            "GIS"));

            schoolAdminClient(SCHOOL_ID)
                    .put()
                    .uri("/api/v1/schools/current")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.email").isEqualTo("updated@gis.edu")
                    .jsonPath("$.data.phone").isEqualTo("+2348011111111")
                    .jsonPath("$.data.address").isEqualTo("34 Updated Avenue")
                    .jsonPath("$.data.city").isEqualTo("Ikeja")
                    .jsonPath("$.data.paymentConfig.paystackPublicKey").isEqualTo("654321")
                    .jsonPath("$.data.paymentConfig.paystackSubaccountCode").isEqualTo("NEWGIS");

            Map<String, Object> updatedSchool = fetchOne("""
                    SELECT email, phone, address, city, logo_url,
                           payment_config ->> 'paystackPublicKey' AS paybill,
                           sms_config ->> 'senderId' AS sender_id,
                           version
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID));
            assertThat(updatedSchool)
                    .containsEntry("email", "updated@gis.edu")
                    .containsEntry("phone", "+2348011111111")
                    .containsEntry("address", "34 Updated Avenue")
                    .containsEntry("city", "Ikeja")
                    .containsEntry("logo_url", "https://cdn.example.com/new-logo.png")
                    .containsEntry("paybill", "654321")
                    .containsEntry("sender_id", "GIS");
            assertThat(((Number) updatedSchool.get("version")).intValue()).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should reject update for unauthenticated user")
        void shouldRejectUpdateForUnauthenticatedUser() {
            webTestClient
                    .put()
                    .uri("/api/v1/schools/current")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateSchoolRequest(
                            "updated@gis.edu",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject update for parent role")
        void shouldRejectUpdateForParentRole() {
            seedSchoolWithCurrentTerm();

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .put()
                    .uri("/api/v1/schools/current")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new UpdateSchoolRequest(
                            "updated@gis.edu",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null))
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Should deactivate school for super admin")
        void shouldDeactivateSchoolForSuperAdmin() {
            seedSchoolWithCurrentTerm();

            superAdminClient()
                    .patch()
                    .uri("/api/v1/schools/{schoolId}/deactivate", SCHOOL_ID)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data").doesNotExist();

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("is_active", false);
        }

        @Test
        @DisplayName("Should deactivate school for super admin using PUT")
        void shouldDeactivateSchoolForSuperAdminUsingPut() {
            seedSchoolWithCurrentTerm();

            superAdminClient()
                    .put()
                    .uri("/api/v1/schools/{schoolId}/deactivate", SCHOOL_ID)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data").doesNotExist();

            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("is_active", false);
        }

        @Test
        @DisplayName("Should reject deactivation for non-super-admin user")
        void shouldRejectDeactivationForNonSuperAdminUser() {
            seedSchoolWithCurrentTerm();

            schoolAdminClient(SCHOOL_ID)
                    .patch()
                    .uri("/api/v1/schools/{schoolId}/deactivate", SCHOOL_ID)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Should return bad request when deactivating missing school")
        void shouldReturnBadRequestWhenDeactivatingMissingSchool() {
            superAdminClient()
                    .patch()
                    .uri("/api/v1/schools/{schoolId}/deactivate", SCHOOL_ID)
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }
    }

    private WebTestClient superAdminClient() {
        return authenticatedClient(null, "SUPER_ADMIN", "SUPER_ADMIN");
    }

    private WebTestClient schoolAdminClient(UUID schoolId) {
        return authenticatedClient(schoolId, "SCHOOL_ADMIN", "SCHOOL_ADMIN", "ACCOUNTANT");
    }

    private WebTestClient authenticatedClient(UUID schoolId, String userType, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID.toString())
                        .claim("preferred_username", "testuser")
                        .claim("email", "test@school.edu")
                        .claim("given_name", "Test")
                        .claim("family_name", "User")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", schoolId != null ? schoolId.toString() : "*")
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private CreateSchoolRequest validCreateSchoolRequest(String schoolCode) {
        return new CreateSchoolRequest(
                "Grace International School",
                schoolCode,
                "hello-" + schoolCode.toLowerCase() + "@gis.edu",
                "+2348012345678",
                "12 School Road",
                "Lagos",
                "Lagos",
                "Nigeria",
                "https://cdn.example.com/logo.png",
                new CreateSchoolRequest.PaymentConfig(
                        "123456",
                        schoolCode,
                        List.of("PAYSTACK", "CARD"),
                        "FLWPUBK_TEST",
                        "FLWSECK_TEST"),
                new CreateSchoolRequest.SmsConfig(
                        "AFRICASTALKING",
                        "secret-key",
                        "gis-user",
                        schoolCode,
                        "234"),
                new CreateSchoolRequest.TermConfig(
                        3,
                        List.of("First Term", "Second Term", "Third Term"),
                        "09-08"),
                new CreateSchoolRequest.AdminUser(
                        "admin-" + schoolCode.toLowerCase() + "@gis.edu",
                        "Ada",
                        "Lovelace",
                        "+2348098765432"));
    }

    private CreateAcademicSessionRequest validCreateAcademicSessionRequest(boolean setAsCurrent) {
        return new CreateAcademicSessionRequest(
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                List.of(
                        new CreateAcademicSessionRequest.TermRequest(
                                "Second Term",
                                2,
                                LocalDate.of(2027, 1, 5),
                                LocalDate.of(2027, 4, 4)),
                        new CreateAcademicSessionRequest.TermRequest(
                                "First Term",
                                1,
                                LocalDate.of(2026, 9, 8),
                                LocalDate.of(2026, 12, 19))),
                setAsCurrent);
    }

    private void seedSchoolWithCurrentTerm() {
        seedSchool(SCHOOL_ID, "Grace International School", "GIS");
        AcademicSession session = AcademicSession.builder()
                .schoolId(SCHOOL_ID)
                .name("2025/2026 Academic Year")
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2026, 9, 7))
                .isCurrent(true)
                .build();
        AcademicSession savedSession = sessionRepository.save(session).block();

        Term term = Term.builder()
                .sessionId(savedSession.getId())
                .name("First Term")
                .termNumber((short) 1)
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2025, 12, 19))
                .isCurrent(true)
                .build();
        termRepository.save(term).block();
    }

    private void seedSchool(UUID schoolId, String schoolName, String schoolCode) {
        seedSchool(schoolId, schoolName, schoolCode, true);
    }

    private void seedSchool(UUID schoolId, String schoolName, String schoolCode, boolean active) {
        JsonNode paymentConfig = objectMapper.valueToTree(Map.of(
                "paystackPublicKey", "123456",
                "paystackSubaccountCode", schoolCode,
                "acceptedPaymentMethods", List.of("PAYSTACK", "CARD")));
        JsonNode smsConfig = objectMapper.valueToTree(Map.of(
                "provider", "AFRICASTALKING",
                "senderId", schoolCode));

        School school = School.builder()
                .id(schoolId)
                .name(schoolName)
                .code(schoolCode)
                .email("hello-" + schoolCode.toLowerCase() + "@gis.edu")
                .phone("+2348012345678")
                .address("12 School Road")
                .city("Lagos")
                .state("Lagos")
                .country("Nigeria")
                .logoUrl("https://cdn.example.com/logo.png")
                .paymentConfig(paymentConfig)
                .smsConfig(smsConfig)
                .termConfig(objectMapper.createObjectNode())
                .isActive(active)
                .build();
        schoolRepository.save(school).block();
    }

    private AcademicSession seedSessionWithTerms(
            UUID schoolId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean current,
            List<TermSeed> termSeeds) {
        AcademicSession session = AcademicSession.builder()
                .schoolId(schoolId)
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .isCurrent(current)
                .build();
        AcademicSession savedSession = sessionRepository.save(session).block();

        List<Term> terms = termSeeds.stream()
                .map(seed -> Term.builder()
                        .sessionId(savedSession.getId())
                        .name(seed.name())
                        .termNumber((short) seed.termNumber())
                        .startDate(startDate.plusMonths(seed.termNumber() - 1L))
                        .endDate(startDate.plusMonths(seed.termNumber()).minusDays(1))
                        .isCurrent(seed.current())
                        .build())
                .toList();
        termRepository.saveAll(terms).collectList().block();

        return savedSession;
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM outbox.outbox_events").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notifications").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notification_templates").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_categories").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.terms").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.academic_sessions").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }

    private long countRows(String sql) {
        return ((Number) databaseClient.sql(sql)
                .map((row, metadata) -> row.get("count"))
                .one()
                .block()).longValue();
    }

    private long countRows(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return ((Number) spec
                .map((row, metadata) -> row.get("count"))
                .one()
                .block()).longValue();
    }

    private UUID findTermId(UUID sessionId, String termName) {
        return (UUID) fetchOne("""
                SELECT id
                FROM school.terms
                WHERE session_id = :sessionId AND name = :termName
                """, Map.of("sessionId", sessionId, "termName", termName))
                .get("id");
    }

    private Map<String, Object> fetchOne(String sql) {
        return fetchOne(sql, Map.of());
    }

    private Map<String, Object> fetchOne(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return spec.fetch().one().block();
    }

    private record TermSeed(String name, int termNumber, boolean current) {
    }
}
