package com.fee.app.schoolfeeapp.school.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.dto.request.CloseSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.CreateAcademicSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.CreateSchoolRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSchoolRequest;
import com.fee.app.schoolfeeapp.school.dto.response.AcademicSessionResponse;
import com.fee.app.schoolfeeapp.school.dto.response.SchoolSummaryResponse;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for SchoolServiceImpl with real PostgreSQL database.
 * These tests verify service layer behavior with:
 * - Real PostgreSQL database via Testcontainers
 * - Spring Boot application context
 * - Transactional operators and reactive streams
 * - Actual database state verification after operations
 * External dependencies (Keycloak, JWT utils) are mocked to focus on
 * service orchestration and database interactions.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SchoolServiceImplIntegrationTest {

    // ========================================================================
    // TESTCONTAINERS CONFIGURATION
    // ========================================================================

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // R2DBC URL (for the app)
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        // JDBC URL (for Flyway)
        registry.add("spring.flyway.url", () ->
                String.format("jdbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private SchoolServiceImpl schoolService;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private AcademicSessionRepository sessionRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID ADMIN_USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID THIRD_SCHOOL_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID ADMIN_KEYCLOAK_ID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final String SCHOOL_NAME = "Grace International School";

    private SchoolFeeUser adminUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(keycloakAdminService, jwtUtils, reactiveJwtDecoder);

        adminUser = SchoolFeeUser.builder()
                .userId(ADMIN_USER_ID)
                .email("admin@gis.edu")
                .firstName("Ada")
                .lastName("Lovelace")
                .userType("SCHOOL_ADMIN")
                .schoolId(SCHOOL_ID)
                .schoolName(SCHOOL_NAME)
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    // ========================================================================
    // CREATE SCHOOL INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create School - Service Integration Tests")
    class CreateSchoolIntegrationTests {

        @Test
        @DisplayName("Should create school successfully with default session, terms, admin, and outbox event")
        void shouldCreateSchoolSuccessfullyWithAllSteps() {
            // Arrange
            CreateSchoolRequest request = validCreateSchoolRequest("GIS");
            when(keycloakAdminService.createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name())))
                    .thenReturn(Mono.just(ADMIN_KEYCLOAK_ID));

            // Act & Assert
            StepVerifier.create(schoolService.createSchool(request))
                    .assertNext(response -> {
                        assertThat(response.schoolId()).isNotNull();
                        assertThat(response.name()).isEqualTo(request.name());
                        assertThat(response.code()).isEqualTo("GIS");
                        assertThat(response.status()).isEqualTo("ACTIVE");
                        assertThat(response.adminUserCreated()).isTrue();
                        assertThat(response.adminTemporaryPassword()).isEqualTo("Sent via email");
                        assertThat(response.currentSessionId()).isNotNull();
                        assertThat(response.currentSessionName()).contains("Academic Year");
                        assertThat(response.message()).contains(request.adminUser().email());
                    })
                    .verifyComplete();

            // Verify data was actually persisted to database
            StepVerifier.create(schoolRepository.findByCode("GIS"))
                    .assertNext(school -> {
                        assertThat(school.getName()).isEqualTo(request.name());
                        assertThat(school.getCountry()).isEqualTo("Nigeria");
                        assertThat(school.getPaymentConfig().path("paystackPublicKey").asText()).isEqualTo("123456");
                        assertThat(school.getSmsConfig().path("senderId").asText()).isEqualTo("GIS");
                        assertThat(school.getIsActive()).isTrue();
                        assertThat(school.getVersion()).isNotNull();
                    })
                    .verifyComplete();

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
                    SELECT aggregate_type, event_type, status,
                           payload ->> 'schoolCode' AS school_code,
                           payload ->> 'adminKeycloakId' AS admin_keycloak_id,
                           jsonb_array_length(payload -> 'termIds') AS term_count
                    FROM outbox.outbox_events
                    WHERE event_type = 'SCHOOL_CREATED'
                    """);
            assertThat(outboxEvent)
                    .containsEntry("aggregate_type", "SCHOOL")
                    .containsEntry("event_type", "SCHOOL_CREATED")
                    .containsEntry("status", "PENDING")
                    .containsEntry("school_code", "GIS")
                    .containsEntry("admin_keycloak_id", ADMIN_KEYCLOAK_ID.toString());
            assertThat(((Number) outboxEvent.get("term_count")).intValue()).isEqualTo(3);

            verify(keycloakAdminService)
                    .createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name()));
        }

        @Test
        @DisplayName("Should create school with manual admin setup when identity provider fails")
        void shouldCreateSchoolWithManualAdminSetupWhenIdentityProviderFails() {
            // Arrange
            CreateSchoolRequest request = validCreateSchoolRequest("GMS");
            when(keycloakAdminService.createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name())))
                    .thenReturn(Mono.error(new RuntimeException("Keycloak unavailable")));

            // Act & Assert
            StepVerifier.create(schoolService.createSchool(request))
                    .assertNext(response -> {
                        assertThat(response.code()).isEqualTo("GMS");
                        assertThat(response.adminUserCreated()).isFalse();
                        assertThat(response.adminTemporaryPassword()).isEqualTo("Manual setup required");
                        assertThat(response.message()).contains("admin account setup failed");
                    })
                    .verifyComplete();

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.schools WHERE code = 'GMS'"))
                    .isEqualTo(1);
            assertThat(countRows("SELECT COUNT(*) AS count FROM school.terms"))
                    .isEqualTo(3);

            Map<String, Object> outboxEvent = fetchOne("""
                    SELECT payload ->> 'adminKeycloakId' AS admin_keycloak_id
                    FROM outbox.outbox_events
                    WHERE event_type = 'SCHOOL_CREATED'
                    """);
            assertThat(outboxEvent.get("admin_keycloak_id")).isNull();
        }

        @Test
        @DisplayName("Should return duplicate resource when school code already exists")
        void shouldReturnDuplicateResourceWhenSchoolCodeAlreadyExists() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS");
            CreateSchoolRequest request = validCreateSchoolRequest("GIS");

            // Act & Assert
            StepVerifier.create(schoolService.createSchool(request))
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException exception &&
                                    exception.getErrorCode().equals("DUPLICATE_RESOURCE") &&
                                    exception.getField().equals("code"))
                    .verify();

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.schools WHERE code = 'GIS'"))
                    .isEqualTo(1);
            assertThat(countRows("SELECT COUNT(*) AS count FROM outbox.outbox_events"))
                    .isZero();
            verify(keycloakAdminService, never())
                    .createStaffUser(any(CreateStaffRequest.class), any(UUID.class), any());
        }

        @Test
        @DisplayName("Should create configured terms from request term config")
        void shouldCreateConfiguredTermsFromRequestTermConfig() {
            // Arrange
            CreateSchoolRequest request = createSchoolRequest(
                    "BAS",
                    new CreateSchoolRequest.TermConfig(
                            2,
                            List.of("Michaelmas", "Lent"),
                            "09-01"));
            when(keycloakAdminService.createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name())))
                    .thenReturn(Mono.just(ADMIN_KEYCLOAK_ID));

            // Act & Assert
            StepVerifier.create(schoolService.createSchool(request))
                    .assertNext(response -> {
                        assertThat(response.code()).isEqualTo("BAS");
                        assertThat(response.currentSessionName()).contains("Academic Year");
                    })
                    .verifyComplete();

            StepVerifier.create(termRepository.findAll().collectList())
                    .assertNext(terms -> {
                        assertThat(terms).hasSize(2);
                        assertThat(terms).extracting(Term::getName)
                                .containsExactlyInAnyOrder("Michaelmas", "Lent");
                        assertThat(terms).filteredOn(Term::getIsCurrent).hasSize(1);
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // READ SCHOOL INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Read School - Service Integration Tests")
    class ReadSchoolIntegrationTests {

        @Test
        @DisplayName("Should return current school with current term and payment config")
        void shouldReturnCurrentSchoolWithCurrentTermAndPaymentConfig() {
            // Arrange
            seedSchoolWithCurrentTerm();

            // Act & Assert
            StepVerifier.create(schoolService.getCurrentSchool())
                    .assertNext(response -> {
                        assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(response.name()).isEqualTo(SCHOOL_NAME);
                        assertThat(response.code()).isEqualTo("GIS");
                        assertThat(response.status()).isEqualTo("ACTIVE");
                        assertThat(response.currentTerm()).isNotNull();
                        assertThat(response.currentTerm().name()).isEqualTo("First Term");
                        assertThat(response.currentTerm().sessionName()).isEqualTo("2025/2026 Academic Year");
                        assertThat(response.paymentConfig())
                                .containsEntry("paystackPublicKey", "123456")
                                .containsEntry("paystackSubaccountCode", "GIS");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail current school lookup when school is missing")
        void shouldFailCurrentSchoolLookupWhenSchoolIsMissing() {
            // Act & Assert
            StepVerifier.create(schoolService.getCurrentSchool())
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException exception &&
                                    exception.getErrorCode().equals("SCHOOL_NOT_FOUND"))
                    .verify();
        }

        @Test
        @DisplayName("Should return school by id for active school")
        void shouldReturnSchoolByIdForActiveSchool() {
            // Arrange
            seedSchoolWithCurrentTerm();

            // Act & Assert
            StepVerifier.create(schoolService.getSchoolById(SCHOOL_ID))
                    .assertNext(response -> {
                        assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(response.currentTerm()).isNotNull();
                        assertThat(response.currentTerm().name()).isEqualTo("First Term");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail school by id lookup for inactive school")
        void shouldFailSchoolByIdLookupForInactiveSchool() {
            // Arrange
            seedSchool(OTHER_SCHOOL_ID, "Inactive School", "INS", false);

            // Act & Assert
            StepVerifier.create(schoolService.getSchoolById(OTHER_SCHOOL_ID))
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException exception &&
                                    exception.getErrorCode().equals("SCHOOL_NOT_FOUND"))
                    .verify();
        }
    }

    // ========================================================================
    // ACADEMIC SESSION LISTING INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Academic Session Listing - Service Integration Tests")
    class AcademicSessionListingIntegrationTests {

        @Test
        @DisplayName("Should return current school sessions ordered with ordered terms")
        void shouldReturnCurrentSchoolSessionsOrderedWithOrderedTerms() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
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
            seedSessionWithTerms(
                    OTHER_SCHOOL_ID,
                    "Other Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    true,
                    List.of(new TermSeed("Other Term", 1, true)));

            // Act & Assert
            StepVerifier.create(schoolService.getCurrentSchoolSessions())
                    .assertNext(sessions -> {
                        assertThat(sessions)
                                .extracting(AcademicSessionResponse::name)
                                .containsExactly(
                                        "2026/2027 Academic Year",
                                        "2025/2026 Academic Year");
                        assertThat(sessions)
                                .extracting(AcademicSessionResponse::isCurrent)
                                .containsExactly(false, true);

                        AcademicSessionResponse latestSession = sessions.getFirst();
                        assertThat(latestSession.terms())
                                .extracting(AcademicSessionResponse.TermResponse::termNumber)
                                .containsExactly(1, 2);
                        assertThat(latestSession.terms())
                                .extracting(AcademicSessionResponse.TermResponse::name)
                                .containsExactly("First Term", "Second Term");

                        AcademicSessionResponse previousSession = sessions.get(1);
                        assertThat(previousSession.terms()).hasSize(1);
                        assertThat(previousSession.terms().getFirst().name()).isEqualTo("First Term");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty current school sessions list")
        void shouldReturnEmptyCurrentSchoolSessionsList() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            seedSessionWithTerms(
                    OTHER_SCHOOL_ID,
                    "Other Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    true,
                    List.of(new TermSeed("Other Term", 1, true)));

            // Act & Assert
            StepVerifier.create(schoolService.getCurrentSchoolSessions())
                    .assertNext(sessions -> assertThat(sessions).isEmpty())
                    .verifyComplete();
        }
    }

    // ========================================================================
    // CREATE ACADEMIC SESSION INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Academic Session - Service Integration Tests")
    class CreateAcademicSessionIntegrationTests {

        @Test
        @DisplayName("Should create academic session without changing existing current session")
        void shouldCreateAcademicSessionWithoutChangingExistingCurrentSession() {
            // Arrange
            seedSchoolWithCurrentTerm();
            CreateAcademicSessionRequest request = validCreateAcademicSessionRequest(false);

            // Act & Assert
            StepVerifier.create(schoolService.createSession(request))
                    .assertNext(response -> {
                        assertThat(response.name()).isEqualTo("2026/2027 Academic Year");
                        assertThat(response.isCurrent()).isFalse();
                        assertThat(response.terms())
                                .extracting(AcademicSessionResponse.TermResponse::termNumber)
                                .containsExactly(1, 2);
                        assertThat(response.terms())
                                .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                                .containsExactly(false, false);
                    })
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId AND is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(2);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms t
                    JOIN school.academic_sessions s ON s.id = t.session_id
                    WHERE s.school_id = :schoolId AND t.is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should create current academic session and move current term to new session")
        void shouldCreateCurrentAcademicSessionAndMoveCurrentTermToNewSession() {
            // Arrange
            seedSchoolWithCurrentTerm();
            CreateAcademicSessionRequest request = validCreateAcademicSessionRequest(true);

            // Act & Assert
            StepVerifier.create(schoolService.createSession(request))
                    .assertNext(response -> {
                        assertThat(response.name()).isEqualTo("2026/2027 Academic Year");
                        assertThat(response.isCurrent()).isTrue();
                        assertThat(response.terms())
                                .extracting(AcademicSessionResponse.TermResponse::termNumber)
                                .containsExactly(1, 2);
                        assertThat(response.terms())
                                .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                                .containsExactly(true, false);
                    })
                    .verifyComplete();

            Map<String, Object> currentSession = fetchOne("""
                    SELECT id, name
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId AND is_current = true
                    """, Map.of("schoolId", SCHOOL_ID));
            assertThat(currentSession).containsEntry("name", "2026/2027 Academic Year");

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId AND is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms t
                    JOIN school.academic_sessions s ON s.id = t.session_id
                    WHERE s.school_id = :schoolId AND t.is_current = true
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
        @DisplayName("Should reject invalid academic session without persisting anything")
        void shouldRejectInvalidAcademicSessionWithoutPersistingAnything() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            // Act & Assert
            StepVerifier.create(schoolService.createSession(request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                        assertThat(exception.getField()).isEqualTo("terms");
                    })
                    .verify();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isZero();
            assertThat(countRows("SELECT COUNT(*) AS count FROM school.terms"))
                    .isZero();
        }

        @Test
        @DisplayName("Should reject academic session creation for inactive school")
        void shouldRejectAcademicSessionCreationForInactiveSchool() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", false);
            CreateAcademicSessionRequest request = validCreateAcademicSessionRequest(false);

            // Act & Assert
            StepVerifier.create(schoolService.createSession(request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isZero();
        }
    }

    @Nested
    @DisplayName("Set Current Academic Session - Service Integration Tests")
    class SetCurrentAcademicSessionIntegrationTests {

        @Test
        @DisplayName("Should set session current and move current term to target session")
        void shouldSetSessionCurrentAndMoveCurrentTermToTargetSession() {
            // Arrange
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

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentSession(targetSession.getId()))
                    .assertNext(response -> {
                        assertThat(response.sessionId()).isEqualTo(targetSession.getId());
                        assertThat(response.name()).isEqualTo("2026/2027 Academic Year");
                        assertThat(response.isCurrent()).isTrue();
                        assertThat(response.terms())
                                .extracting(AcademicSessionResponse.TermResponse::termNumber)
                                .containsExactly(1, 2);
                        assertThat(response.terms())
                                .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                                .containsExactly(true, false);
                    })
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId AND is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(fetchOne("""
                    SELECT id, name
                    FROM school.academic_sessions
                    WHERE school_id = :schoolId AND is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("id", targetSession.getId())
                    .containsEntry("name", "2026/2027 Academic Year");
            assertThat(fetchOne("""
                    SELECT t.name AS term_name, s.name AS session_name
                    FROM school.terms t
                    JOIN school.academic_sessions s ON s.id = t.session_id
                    WHERE s.school_id = :schoolId AND t.is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("term_name", "First Term")
                    .containsEntry("session_name", "2026/2027 Academic Year");
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms t
                    JOIN school.academic_sessions s ON s.id = t.session_id
                    WHERE s.school_id = :schoolId AND t.is_current = true
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should repair current term when target session is already current")
        void shouldRepairCurrentTermWhenTargetSessionIsAlreadyCurrent() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession targetSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2026/2027 Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    true,
                    List.of(
                            new TermSeed("Second Term", 2, false),
                            new TermSeed("First Term", 1, false)));

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentSession(targetSession.getId()))
                    .assertNext(response -> {
                        assertThat(response.sessionId()).isEqualTo(targetSession.getId());
                        assertThat(response.isCurrent()).isTrue();
                        assertThat(response.terms())
                                .extracting(AcademicSessionResponse.TermResponse::isCurrent)
                                .containsExactly(true, false);
                    })
                    .verifyComplete();

            assertThat(fetchOne("""
                    SELECT t.name AS term_name, t.term_number
                    FROM school.terms t
                    WHERE t.session_id = :sessionId AND t.is_current = true
                    """, Map.of("sessionId", targetSession.getId())))
                    .containsEntry("term_name", "First Term")
                    .containsEntry("term_number", (short) 1);
        }

        @Test
        @DisplayName("Should reject setting current session for another school")
        void shouldRejectSettingCurrentSessionForAnotherSchool() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Other School", "OSS", true);
            AcademicSession otherSchoolSession = seedSessionWithTerms(
                    OTHER_SCHOOL_ID,
                    "Other Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, false)));

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentSession(otherSchoolSession.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_IN_SCHOOL");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject setting current missing session")
        void shouldRejectSettingCurrentMissingSession() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentSession(UUID.randomUUID()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_NOT_FOUND");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject setting current session for inactive school")
        void shouldRejectSettingCurrentSessionForInactiveSchool() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", false);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2026/2027 Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, false)));

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentSession(session.getId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("Set Current Term - Service Integration Tests")
    class SetCurrentTermIntegrationTests {

        @Test
        @DisplayName("Should set term current within current session")
        void shouldSetTermCurrentWithinCurrentSession() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentTerm(secondTermId))
                    .assertNext(response -> {
                        assertThat(response.termId()).isEqualTo(secondTermId);
                        assertThat(response.name()).isEqualTo("Second Term");
                        assertThat(response.sessionName()).isEqualTo("2025/2026 Academic Year");
                        assertThat(response.isCurrent()).isTrue();
                        assertThat(response.previousCurrentTerm()).isNotNull();
                        assertThat(response.previousCurrentTerm().termId()).isEqualTo(firstTermId);
                        assertThat(response.previousCurrentTerm().name()).isEqualTo("First Term");
                        assertThat(response.previousCurrentTerm().status()).isEqualTo("COMPLETED");
                    })
                    .verifyComplete();

            assertThat(fetchOne("""
                    SELECT is_current, status, completed_at, completed_by
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", firstTermId)))
                    .containsEntry("is_current", false)
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("completed_by", ADMIN_USER_ID);
            assertThat(fetchOne("""
                    SELECT is_current, status, completed_at, completed_by
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", secondTermId)))
                    .containsEntry("is_current", true)
                    .containsEntry("status", "ACTIVE")
                    .containsEntry("completed_at", null)
                    .containsEntry("completed_by", null);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms
                    WHERE session_id = :sessionId AND is_current = true
                    """, Map.of("sessionId", session.getId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should return existing current term without changing state")
        void shouldReturnExistingCurrentTermWithoutChangingState() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentTerm(firstTermId))
                    .assertNext(response -> {
                        assertThat(response.termId()).isEqualTo(firstTermId);
                        assertThat(response.isCurrent()).isTrue();
                        assertThat(response.previousCurrentTerm()).isNull();
                    })
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms
                    WHERE session_id = :sessionId AND is_current = true
                    """, Map.of("sessionId", session.getId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject term outside current session")
        void shouldRejectTermOutsideCurrentSession() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentTerm(oldTermId))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("TERM_NOT_IN_CURRENT_SESSION");
                        assertThat(exception.getField()).isEqualTo("termId");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT is_current
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", oldTermId)))
                    .containsEntry("is_current", false);
        }

        @Test
        @DisplayName("Should reject term in completed current session")
        void shouldRejectTermInCompletedCurrentSession() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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
                    .bind("closedBy", ADMIN_USER_ID)
                    .bind("sessionId", session.getId())
                    .fetch()
                    .rowsUpdated()
                    .block();

            // Act & Assert
            StepVerifier.create(schoolService.setCurrentTerm(termId))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should not set current term after concurrent school deactivation commits")
        void shouldNotSetCurrentTermAfterConcurrentSchoolDeactivationCommits() throws Exception {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            CompletableFuture<Throwable> setCurrentTermFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                setCurrentTermFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        schoolService.setCurrentTerm(secondTermId).block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(setCurrentTermFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = setCurrentTermFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (setCurrentTermFuture != null && !setCurrentTermFuture.isDone()) {
                    setCurrentTermFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT is_current
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", secondTermId)))
                    .containsEntry("is_current", false);
        }
    }

    @Nested
    @DisplayName("Update Academic Session - Service Integration Tests")
    class UpdateAcademicSessionIntegrationTests {

        @Test
        @DisplayName("Should update academic session fields and selected terms")
        void shouldUpdateAcademicSessionFieldsAndSelectedTerms() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            // Act & Assert
            StepVerifier.create(schoolService.updateSession(session.getId(), request))
                    .assertNext(response -> {
                        assertThat(response.sessionId()).isEqualTo(session.getId());
                        assertThat(response.name()).isEqualTo("2025/2026 Revised Academic Year");
                        assertThat(response.updatedAt()).isNotNull();
                    })
                    .verifyComplete();

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
        @DisplayName("Should not update session after concurrent school deactivation commits")
        void shouldNotUpdateSessionAfterConcurrentSchoolDeactivationCommits() throws Exception {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));
            UpdateSessionRequest request = new UpdateSessionRequest(
                    "Should Not Persist",
                    null,
                    null,
                    null);

            CompletableFuture<Throwable> updateFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                updateFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        schoolService.updateSession(session.getId(), request)
                                .block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(updateFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = updateFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (updateFuture != null && !updateFuture.isDone()) {
                    updateFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT name
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", session.getId())))
                    .containsEntry("name", "2025/2026 Academic Year");
            assertThat(fetchOne("""
                    SELECT is_active
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("is_active", false);
        }

        @Test
        @DisplayName("Should reject overlapping term update without persisting changes")
        void shouldRejectOverlappingTermUpdateWithoutPersistingChanges() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            // Act & Assert
            StepVerifier.create(schoolService.updateSession(session.getId(), request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                        assertThat(exception.getField()).isEqualTo("terms");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT name
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", session.getId())))
                    .containsEntry("name", "2025/2026 Academic Year");
            assertThat(fetchOne("""
                    SELECT name, start_date
                    FROM school.terms
                    WHERE id = :termId
                    """, Map.of("termId", secondTermId)))
                    .containsEntry("name", "Second Term")
                    .containsEntry("start_date", LocalDate.of(2025, 10, 8));
        }

        @Test
        @DisplayName("Should reject session date update that would exclude existing terms")
        void shouldRejectSessionDateUpdateThatWouldExcludeExistingTerms() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));

            UpdateSessionRequest request = new UpdateSessionRequest(
                    null,
                    null,
                    LocalDate.of(2025, 9, 30),
                    null);

            // Act & Assert
            StepVerifier.create(schoolService.updateSession(session.getId(), request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("INVALID_TERM_CONFIG");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT end_date
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", session.getId())))
                    .containsEntry("end_date", LocalDate.of(2026, 9, 7));
        }

        @Test
        @DisplayName("Should reject term update for term in another session")
        void shouldRejectTermUpdateForTermInAnotherSession() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession targetSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));
            AcademicSession otherSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2026/2027 Academic Year",
                    LocalDate.of(2026, 9, 8),
                    LocalDate.of(2027, 9, 7),
                    false,
                    List.of(new TermSeed("Other First Term", 1, false)));
            UUID otherSessionTermId = findTermId(otherSession.getId(), "Other First Term");

            UpdateSessionRequest request = new UpdateSessionRequest(
                    null,
                    null,
                    null,
                    List.of(new UpdateSessionRequest.TermUpdate(
                            otherSessionTermId,
                            "Wrong Session Term",
                            null,
                            null)));

            // Act & Assert
            StepVerifier.create(schoolService.updateSession(targetSession.getId(), request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("TERM_NOT_IN_SESSION");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject update for inactive school")
        void shouldRejectUpdateForInactiveSchool() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", false);
            AcademicSession session = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));

            UpdateSessionRequest request = new UpdateSessionRequest(
                    "Should Not Persist",
                    null,
                    null,
                    null);

            // Act & Assert
            StepVerifier.create(schoolService.updateSession(session.getId(), request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();
        }
    }

    // ========================================================================
    // CLOSE SESSION INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Close Academic Session - Service Integration Tests")
    class CloseAcademicSessionIntegrationTests {

        @Test
        @DisplayName("Should close non-current session and complete all terms")
        void shouldCloseNonCurrentSessionAndCompleteAllTerms() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
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

            // Act & Assert
            StepVerifier.create(schoolService.closeSession(closingSession.getId(), request))
                    .assertNext(response -> {
                        assertThat(response.sessionId()).isEqualTo(closingSession.getId());
                        assertThat(response.name()).isEqualTo("2024/2025 Academic Year");
                        assertThat(response.status()).isEqualTo("COMPLETED");
                        assertThat(response.closedAt()).isNotNull();
                        assertThat(response.termsCompleted()).isEqualTo(2);
                        assertThat(response.studentsArchived()).isZero();
                        assertThat(response.message()).contains("Terms marked completed");
                        assertThat(response.message()).contains("Student archiving is not yet implemented");
                    })
                    .verifyComplete();

            Map<String, Object> closedSession = fetchOne("""
                    SELECT status, is_current, closed_at, closed_by, closed_notes
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", closingSession.getId()));
            assertThat(closedSession)
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("is_current", false)
                    .containsEntry("closed_by", ADMIN_USER_ID)
                    .containsEntry("closed_notes", "End of year reconciliation complete");
            assertThat(closedSession.get("closed_at")).isNotNull();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms
                    WHERE session_id = :sessionId
                      AND status = 'COMPLETED'
                      AND is_current = false
                      AND completed_at IS NOT NULL
                      AND completed_by = :completedBy
                    """, Map.of("sessionId", closingSession.getId(), "completedBy", ADMIN_USER_ID)))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Should close non-current session without completing terms")
        void shouldCloseNonCurrentSessionWithoutCompletingTerms() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession closingSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2024/2025 Academic Year",
                    LocalDate.of(2024, 9, 8),
                    LocalDate.of(2025, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, true)));

            // Act & Assert
            StepVerifier.create(schoolService.closeSession(
                            closingSession.getId(),
                            new CloseSessionRequest(false, false, null)))
                    .assertNext(response -> {
                        assertThat(response.status()).isEqualTo("COMPLETED");
                        assertThat(response.termsCompleted()).isZero();
                        assertThat(response.message()).isEqualTo("Session closed.");
                    })
                    .verifyComplete();

            assertThat(fetchOne("""
                    SELECT status, closed_at
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", closingSession.getId())))
                    .containsEntry("status", "COMPLETED");
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms
                    WHERE session_id = :sessionId AND status = 'COMPLETED'
                    """, Map.of("sessionId", closingSession.getId())))
                    .isZero();
        }

        @Test
        @DisplayName("Should reject closing current session")
        void shouldRejectClosingCurrentSession() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession currentSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2025/2026 Academic Year",
                    LocalDate.of(2025, 9, 8),
                    LocalDate.of(2026, 9, 7),
                    true,
                    List.of(new TermSeed("First Term", 1, true)));

            // Act & Assert
            StepVerifier.create(schoolService.closeSession(
                            currentSession.getId(),
                            new CloseSessionRequest(true, false, "Close current")))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("CANNOT_CLOSE_CURRENT_SESSION");
                    })
                    .verify();

            assertThat(fetchOne("""
                    SELECT is_current, closed_at
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", currentSession.getId())))
                    .containsEntry("is_current", true)
                    .containsEntry("closed_at", null);
        }

        @Test
        @DisplayName("Should reject closing already completed session")
        void shouldRejectClosingAlreadyCompletedSession() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession closedSession = seedSessionWithTerms(
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
                    .bind("closedBy", ADMIN_USER_ID)
                    .bind("sessionId", closedSession.getId())
                    .fetch()
                    .rowsUpdated()
                    .block();

            // Act & Assert
            StepVerifier.create(schoolService.closeSession(
                            closedSession.getId(),
                            new CloseSessionRequest(true, false, null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SESSION_ALREADY_CLOSED");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should not close session after concurrent school deactivation commits")
        void shouldNotCloseSessionAfterConcurrentSchoolDeactivationCommits() throws Exception {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            AcademicSession closingSession = seedSessionWithTerms(
                    SCHOOL_ID,
                    "2024/2025 Academic Year",
                    LocalDate.of(2024, 9, 8),
                    LocalDate.of(2025, 9, 7),
                    false,
                    List.of(new TermSeed("First Term", 1, true)));

            CompletableFuture<Throwable> closeFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                closeFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        schoolService.closeSession(
                                        closingSession.getId(),
                                        new CloseSessionRequest(true, false, "Should not persist"))
                                .block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(closeFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = closeFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (closeFuture != null && !closeFuture.isDone()) {
                    closeFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT status, closed_at
                    FROM school.academic_sessions
                    WHERE id = :sessionId
                    """, Map.of("sessionId", closingSession.getId())))
                    .containsEntry("closed_at", null);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM school.terms
                    WHERE session_id = :sessionId AND status = 'COMPLETED'
                    """, Map.of("sessionId", closingSession.getId())))
                    .isZero();
        }
    }

    // ========================================================================
    // LIST SCHOOL INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("List School - Service Integration Tests")
    class ListSchoolIntegrationTests {

        @Test
        @DisplayName("Should list active schools with current term summary")
        void shouldListActiveSchoolsWithCurrentTermSummary() {
            // Arrange
            seedSchoolWithCurrentTerm(SCHOOL_ID, SCHOOL_NAME, "GIS", true, "First Term");
            seedSchool(OTHER_SCHOOL_ID, "Inactive School", "INS", false);

            // Act & Assert
            StepVerifier.create(schoolService.listSchools("ACTIVE", PageRequest.of(0, 10)))
                    .assertNext(response -> {
                        assertThat(response.page()).isZero();
                        assertThat(response.size()).isEqualTo(10);
                        assertThat(response.totalElements()).isEqualTo(1);
                        assertThat(response.totalPages()).isEqualTo(1);
                        assertThat(response.content()).hasSize(1);

                        SchoolSummaryResponse summary = response.content().getFirst();
                        assertThat(summary.schoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(summary.code()).isEqualTo("GIS");
                        assertThat(summary.status()).isEqualTo("ACTIVE");
                        assertThat(summary.currentTerm()).isEqualTo("First Term 2025/2026 Academic Year");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should list inactive schools only")
        void shouldListInactiveSchoolsOnly() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Inactive School", "INS", false);

            // Act & Assert
            StepVerifier.create(schoolService.listSchools("INACTIVE", PageRequest.of(0, 10)))
                    .assertNext(response -> {
                        assertThat(response.totalElements()).isEqualTo(1);
                        assertThat(response.totalPages()).isEqualTo(1);
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().getFirst().code()).isEqualTo("INS");
                        assertThat(response.content().getFirst().status()).isEqualTo("INACTIVE");
                        assertThat(response.content().getFirst().currentTerm()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should list all schools with pagination")
        void shouldListAllSchoolsWithPagination() {
            // Arrange
            seedSchool(SCHOOL_ID, SCHOOL_NAME, "GIS", true);
            seedSchool(OTHER_SCHOOL_ID, "Inactive School", "INS", false);
            seedSchool(THIRD_SCHOOL_ID, "Bright Academy", "BAS", true);

            // Act & Assert
            StepVerifier.create(schoolService.listSchools("ALL", PageRequest.of(1, 2)))
                    .assertNext(response -> {
                        assertThat(response.page()).isEqualTo(1);
                        assertThat(response.size()).isEqualTo(2);
                        assertThat(response.totalElements()).isEqualTo(3);
                        assertThat(response.totalPages()).isEqualTo(2);
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.content().getFirst().status())
                                .isIn("ACTIVE", "INACTIVE");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject invalid school list status")
        void shouldRejectInvalidSchoolListStatus() {
            // Act & Assert
            StepVerifier.create(schoolService.listSchools("ARCHIVED", PageRequest.of(0, 10)))
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException exception &&
                                    exception.getErrorCode().equals("INVALID_STATUS") &&
                                    exception.getField().equals("status"))
                    .verify();
        }
    }

    // ========================================================================
    // UPDATE AND DEACTIVATE INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Update And Deactivate School - Service Integration Tests")
    class UpdateAndDeactivateIntegrationTests {

        @Test
        @DisplayName("Should update current school and persist JSON config changes")
        void shouldUpdateCurrentSchoolAndPersistJsonConfigChanges() {
            // Arrange
            School school = seedSchoolWithCurrentTerm();
            Integer initialVersion = school.getVersion();
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

            // Act & Assert
            StepVerifier.create(schoolService.updateSchool(request))
                    .assertNext(response -> {
                        assertThat(response.schoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(response.email()).isEqualTo("updated@gis.edu");
                        assertThat(response.phone()).isEqualTo("+2348011111111");
                        assertThat(response.address()).isEqualTo("34 Updated Avenue");
                        assertThat(response.city()).isEqualTo("Ikeja");
                        assertThat(response.paymentConfig())
                                .containsEntry("paystackPublicKey", "654321")
                                .containsEntry("paystackSubaccountCode", "NEWGIS");
                    })
                    .verifyComplete();

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
            assertThat(((Number) updatedSchool.get("version")).intValue())
                    .isGreaterThan(initialVersion);
        }

        @Test
        @DisplayName("Should fail update when current user's school is missing")
        void shouldFailUpdateWhenCurrentUsersSchoolIsMissing() {
            // Arrange
            UpdateSchoolRequest request = new UpdateSchoolRequest(
                    "updated@gis.edu",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);

            // Act & Assert
            StepVerifier.create(schoolService.updateSchool(request))
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException exception &&
                                    exception.getErrorCode().equals("SCHOOL_NOT_FOUND"))
                    .verify();
        }

        @Test
        @DisplayName("Should deactivate school and increment version")
        void shouldDeactivateSchoolAndIncrementVersion() {
            // Arrange
            School school = seedSchoolWithCurrentTerm();
            Integer initialVersion = school.getVersion();

            // Act & Assert
            StepVerifier.create(schoolService.deactivateSchool(SCHOOL_ID))
                    .verifyComplete();

            Map<String, Object> deactivatedSchool = fetchOne("""
                    SELECT is_active, version
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID));
            assertThat(deactivatedSchool).containsEntry("is_active", false);
            assertThat(((Number) deactivatedSchool.get("version")).intValue())
                    .isGreaterThan(initialVersion);
        }

        @Test
        @DisplayName("Should fail deactivation when school is missing")
        void shouldFailDeactivationWhenSchoolIsMissing() {
            // Act & Assert
            StepVerifier.create(schoolService.deactivateSchool(SCHOOL_ID))
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException exception &&
                                    exception.getErrorCode().equals("SCHOOL_NOT_FOUND"))
                    .verify();
        }
    }

    // ========================================================================
    // TRANSACTIONAL BEHAVIOR TESTS
    // ========================================================================

    @Nested
    @DisplayName("Transactional Behavior Tests")
    class TransactionalBehaviorTests {

        @Test
        @DisplayName("Should rollback school insert when default session creation fails")
        void shouldRollbackSchoolInsertWhenDefaultSessionCreationFails() {
            // Arrange
            CreateSchoolRequest request = createSchoolRequest(
                    "TRX",
                    new CreateSchoolRequest.TermConfig(3, List.of("One", "Two", "Three"), "11-01"));

            // Act & Assert
            StepVerifier.create(schoolService.createSchool(request))
                    .expectErrorMatches(error ->
                            error instanceof SchoolFeeException exception &&
                                    exception.getErrorCode().equals("INVALID_TERM_CONFIG"))
                    .verify();

            assertThat(countRows("SELECT COUNT(*) AS count FROM school.schools WHERE code = 'TRX'"))
                    .isZero();
            assertThat(countRows("SELECT COUNT(*) AS count FROM school.academic_sessions"))
                    .isZero();
            assertThat(countRows("SELECT COUNT(*) AS count FROM school.terms"))
                    .isZero();
            assertThat(countRows("SELECT COUNT(*) AS count FROM outbox.outbox_events"))
                    .isZero();
            verify(keycloakAdminService, never())
                    .createStaffUser(any(CreateStaffRequest.class), any(UUID.class), any());
        }
    }

    // ========================================================================
    // OUTBOX PATTERN TESTS
    // ========================================================================

    @Nested
    @DisplayName("Outbox Pattern Tests")
    class OutboxPatternTests {

        @Test
        @DisplayName("Should create school created outbox event with aggregate payload")
        void shouldCreateSchoolCreatedOutboxEventWithAggregatePayload() {
            // Arrange
            CreateSchoolRequest request = validCreateSchoolRequest("OBS");
            when(keycloakAdminService.createStaffUser(any(CreateStaffRequest.class), any(UUID.class), eq(request.name())))
                    .thenReturn(Mono.just(ADMIN_KEYCLOAK_ID));

            // Act
            StepVerifier.create(schoolService.createSchool(request))
                    .assertNext(response -> assertThat(response.code()).isEqualTo("OBS"))
                    .verifyComplete();

            // Verify outbox event was created in database
            StepVerifier.create(outboxEventRepository.findAll())
                    .assertNext(event -> {
                        assertThat(event.getId()).isNotNull();
                        assertThat(event.getEventType()).isEqualTo("SCHOOL_CREATED");
                        assertThat(event.getAggregateType()).isEqualTo("SCHOOL");
                        assertThat(event.getAggregateId()).isNotNull();
                        assertThat(event.getStatus()).isEqualTo("PENDING");
                        assertThat(event.getPayload().path("schoolCode").asText()).isEqualTo("OBS");
                        assertThat(event.getPayload().path("adminKeycloakId").asText())
                                .isEqualTo(ADMIN_KEYCLOAK_ID.toString());
                        assertThat(event.getPayload().path("sessionId").asText()).isNotBlank();
                        assertThat(event.getPayload().path("termIds")).hasSize(3);
                    })
                    .verifyComplete();
        }
    }

    private CreateSchoolRequest validCreateSchoolRequest(String schoolCode) {
        return createSchoolRequest(
                schoolCode,
                new CreateSchoolRequest.TermConfig(
                        3,
                        List.of("First Term", "Second Term", "Third Term"),
                        "09-08"));
    }

    private CreateSchoolRequest createSchoolRequest(
            String schoolCode,
            CreateSchoolRequest.TermConfig termConfig) {
        return new CreateSchoolRequest(
                SCHOOL_NAME,
                schoolCode,
                "hello-" + schoolCode.toLowerCase() + "@gis.edu",
                "+2348012345678",
                "12 School Road",
                "Lagos",
                "Lagos",
                null,
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
                termConfig,
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

    private School seedSchoolWithCurrentTerm() {
        return seedSchoolWithCurrentTerm(SCHOOL_ID, SCHOOL_NAME, "GIS", true, "First Term");
    }

    private School seedSchoolWithCurrentTerm(
            UUID schoolId,
            String schoolName,
            String schoolCode,
            boolean active,
            String termName) {
        School school = seedSchool(schoolId, schoolName, schoolCode, active);

        AcademicSession session = AcademicSession.builder()
                .schoolId(schoolId)
                .name("2025/2026 Academic Year")
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2026, 9, 7))
                .isCurrent(true)
                .build();
        AcademicSession savedSession = sessionRepository.save(session).block();

        Term term = Term.builder()
                .sessionId(savedSession.getId())
                .name(termName)
                .termNumber((short) 1)
                .startDate(LocalDate.of(2025, 9, 8))
                .endDate(LocalDate.of(2025, 12, 19))
                .isCurrent(true)
                .build();
        termRepository.save(term).block();

        return school;
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

    private School seedSchool(UUID schoolId, String schoolName, String schoolCode) {
        return seedSchool(schoolId, schoolName, schoolCode, true);
    }

    private School seedSchool(UUID schoolId, String schoolName, String schoolCode, boolean active) {
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
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return schoolRepository.save(school).block();
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

    private void lockActiveSchoolRow(Connection connection, UUID schoolId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM school.schools
                WHERE id = ? AND is_active = true
                FOR UPDATE
                """)) {
            statement.setObject(1, schoolId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
            }
        }
    }

    private void deactivateLockedSchool(Connection connection, UUID schoolId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE school.schools
                SET is_active = false
                WHERE id = ?
                """)) {
            statement.setObject(1, schoolId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
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
