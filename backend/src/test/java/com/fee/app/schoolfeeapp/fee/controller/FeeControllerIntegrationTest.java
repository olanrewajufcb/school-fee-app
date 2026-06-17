package com.fee.app.schoolfeeapp.fee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.fee.dto.request.CreateFeeStructureRequest;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class FeeControllerIntegrationTest {

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
    private ClassRepository classRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Nested
    @DisplayName("POST /api/v1/fees/structures")
    class CreateFeeStructureIntegrationTests {

        @Test
        @DisplayName("Should create fee structure for accountant")
        void shouldCreateFeeStructureForAccountant() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedAuthUser(SCHOOL_ID, USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID);
            Term term = seedTerm(session.getId());
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", true);
            UUID categoryId = seedFeeCategory(SCHOOL_ID, "Tuition");
            seedStudent(SCHOOL_ID, cls.getId(), "STU260001", "Ada", "Lovelace", "ACTIVE");

            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/fees/structures")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(session.getId(), term.getId(), cls.getId(), categoryId))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.structureId").exists()
                    .jsonPath("$.data.name").isEqualTo("Primary 1 Tuition")
                    .jsonPath("$.data.totalAmount").isEqualTo(15000)
                    .jsonPath("$.data.applicableClassCount").isEqualTo(1)
                    .jsonPath("$.data.estimatedStudentCount").isEqualTo(1);

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.fee_structures
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should return validation error for blank structure name")
        void shouldReturnValidationErrorForBlankStructureName() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/fees/structures")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateFeeStructureRequest(
                            " ",
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            List.of(UUID.randomUUID()),
                            LocalDate.now().plusDays(30),
                            List.of(new CreateFeeStructureRequest.FeeItemRequest(
                                    null,
                                    "Tuition",
                                    BigDecimal.valueOf(10000),
                                    true,
                                    1)),
                            null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.errors[0].field").isEqualTo("name");
        }

        @Test
        @DisplayName("Should reject create fee structure for teacher role")
        void shouldRejectCreateFeeStructureForTeacherRole() {
            authenticatedClient(SCHOOL_ID, "TEACHER", "TEACHER")
                    .post()
                    .uri("/api/v1/fees/structures")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new CreateFeeStructureRequest(
                            "Primary 1 Tuition",
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            List.of(UUID.randomUUID()),
                            LocalDate.now().plusDays(30),
                            List.of(new CreateFeeStructureRequest.FeeItemRequest(
                                    null,
                                    "Tuition",
                                    BigDecimal.valueOf(10000),
                                    true,
                                    1)),
                            null))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("POST /api/v1/fees/structures/{structureId}/assign")
    class AssignFeesIntegrationTests {

        @Test
        @DisplayName("Should assign fees for school admin")
        void shouldAssignFeesForSchoolAdmin() {
            FeeFixture fixture = seedFeeFixture();

            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .post()
                    .uri("/api/v1/fees/structures/{structureId}/assign", fixture.structureId())
                    .exchange()
                    .expectStatus().isAccepted()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.structureId").isEqualTo(fixture.structureId().toString())
                    .jsonPath("$.data.studentsAssigned").isEqualTo(2)
                    .jsonPath("$.data.totalExpectedAmount").isEqualTo(30000)
                    .jsonPath("$.data.status").isEqualTo("ASSIGNED");

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.student_fees
                    WHERE fee_structure_id = :structureId
                    """, Map.of("structureId", fixture.structureId())))
                    .isEqualTo(2);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE source_entity_id = :structureId
                    """, Map.of("structureId", fixture.structureId())))
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("Should return not found when assigning missing structure")
        void shouldReturnNotFoundWhenAssigningMissingStructure() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedAuthUser(SCHOOL_ID, USER_ID);

            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .post()
                    .uri("/api/v1/fees/structures/{structureId}/assign", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("STRUCTURE_NOT_FOUND");
        }

        @Test
        @DisplayName("Should reject assign fees for teacher role")
        void shouldRejectAssignFeesForTeacherRole() {
            authenticatedClient(SCHOOL_ID, "TEACHER", "TEACHER")
                    .post()
                    .uri("/api/v1/fees/structures/{structureId}/assign", UUID.randomUUID())
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET fee read endpoints")
    class FeeReadEndpointIntegrationTests {

        @Test
        @DisplayName("Should list enriched fee structures")
        void shouldListEnrichedFeeStructures() {
            FeeFixture fixture = seedFeeFixture();
            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .post()
                    .uri("/api/v1/fees/structures/{structureId}/assign", fixture.structureId())
                    .exchange()
                    .expectStatus().isAccepted();
            UUID studentFeeId = findStudentFeeId(fixture.structureId(), fixture.firstStudentId());
            seedPaymentLedgerEntry(
                    studentFeeId,
                    fixture.firstStudentId(),
                    BigDecimal.valueOf(-15000),
                    BigDecimal.ZERO);

            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .get()
                    .uri("/api/v1/fees/structures?status=ACTIVE&termId=current")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data[0].structureId").isEqualTo(fixture.structureId().toString())
                    .jsonPath("$.data[0].termName").isEqualTo("First Term")
                    .jsonPath("$.data[0].mandatoryAmount").isEqualTo(15000)
                    .jsonPath("$.data[0].studentCount").isEqualTo(2)
                    .jsonPath("$.data[0].collectionRate").isEqualTo(50.0)
                    .jsonPath("$.data[0].applicableToClasses[0]").isEqualTo("Primary 1");
        }

        @Test
        @DisplayName("Should return student fees for school admin")
        void shouldReturnStudentFeesForSchoolAdmin() {
            FeeFixture fixture = seedFeeFixture();
            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .post()
                    .uri("/api/v1/fees/structures/{structureId}/assign", fixture.structureId())
                    .exchange()
                    .expectStatus().isAccepted();
            UUID studentFeeId = findStudentFeeId(fixture.structureId(), fixture.firstStudentId());
            seedPaymentLedgerEntry(
                    studentFeeId,
                    fixture.firstStudentId(),
                    BigDecimal.valueOf(-5000),
                    BigDecimal.valueOf(10000));

            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .get()
                    .uri("/api/v1/fees/students/{studentId}", fixture.firstStudentId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data[0].studentFeeId").isEqualTo(studentFeeId.toString())
                    .jsonPath("$.data[0].termName").isEqualTo("First Term")
                    .jsonPath("$.data[0].amountPaid").isEqualTo(5000)
                    .jsonPath("$.data[0].balance").isEqualTo(10000)
                    .jsonPath("$.data[0].status").isEqualTo("PARTIAL");
        }

        @Test
        @DisplayName("Should reject parent without fee access")
        void shouldRejectParentWithoutFeeAccess() {
            FeeFixture fixture = seedFeeFixture();

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .get()
                    .uri("/api/v1/fees/students/{studentId}", fixture.firstStudentId())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("ACCESS_DENIED");
        }

        @Test
        @DisplayName("Should return dashboard for accountant")
        void shouldReturnDashboardForAccountant() {
            FeeFixture fixture = seedFeeFixture();
            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .post()
                    .uri("/api/v1/fees/structures/{structureId}/assign", fixture.structureId())
                    .exchange()
                    .expectStatus().isAccepted();
            UUID studentFeeId = findStudentFeeId(fixture.structureId(), fixture.firstStudentId());
            seedPaymentLedgerEntry(
                    studentFeeId,
                    fixture.firstStudentId(),
                    BigDecimal.valueOf(-15000),
                    BigDecimal.ZERO);

            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .get()
                    .uri("/api/v1/fees/dashboard?termId=current")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.termName").isEqualTo("First Term")
                    .jsonPath("$.data.summary.totalExpected").isEqualTo(30000)
                    .jsonPath("$.data.summary.totalCollected").isEqualTo(15000)
                    .jsonPath("$.data.summary.totalOutstanding").isEqualTo(15000)
                    .jsonPath("$.data.summary.collectionRate").isEqualTo(50.0)
                    .jsonPath("$.data.byClass[0].className").isEqualTo("Primary 1")
                    .jsonPath("$.data.dailyCollectionTrend[0].transactions").isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject dashboard for teacher role")
        void shouldRejectDashboardForTeacherRole() {
            authenticatedClient(SCHOOL_ID, "TEACHER", "TEACHER")
                    .get()
                    .uri("/api/v1/fees/dashboard")
                    .exchange()
                    .expectStatus().isForbidden();
        }
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

    private FeeFixture seedFeeFixture() {
        seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
        seedAuthUser(SCHOOL_ID, USER_ID);
        AcademicSession session = seedSession(SCHOOL_ID);
        Term term = seedTerm(session.getId());
        ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", true);
        UUID categoryId = seedFeeCategory(SCHOOL_ID, "Tuition");
        UUID firstStudentId = seedStudent(SCHOOL_ID, cls.getId(), "STU260010", "Ada", "Lovelace", "ACTIVE");
        UUID secondStudentId = seedStudent(SCHOOL_ID, cls.getId(), "STU260011", "Katherine", "Johnson", "ACTIVE");
        UUID structureId = seedFeeStructure(SCHOOL_ID, session.getId(), term.getId(), USER_ID);
        seedFeeStructureItem(structureId, categoryId);
        seedFeeStructureClass(structureId, cls.getId());
        return new FeeFixture(structureId, term.getId(), cls.getId(), firstStudentId, secondStudentId);
    }

    private CreateFeeStructureRequest validRequest(UUID sessionId, UUID termId, UUID classId, UUID categoryId) {
        return new CreateFeeStructureRequest(
                " Primary 1 Tuition ",
                sessionId,
                termId,
                List.of(classId),
                LocalDate.now().plusDays(30),
                List.of(
                        new CreateFeeStructureRequest.FeeItemRequest(
                                categoryId,
                                " Tuition ",
                                BigDecimal.valueOf(10000),
                                true,
                                1),
                        new CreateFeeStructureRequest.FeeItemRequest(
                                null,
                                " Sports ",
                                BigDecimal.valueOf(5000),
                                false,
                                2)),
                new CreateFeeStructureRequest.LateFeeConfig(
                        14,
                        5.0,
                        BigDecimal.valueOf(500)));
    }

    private School seedSchool(UUID schoolId, String schoolName, String schoolCode, boolean active) {
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
                .paymentConfig(objectMapper.createObjectNode())
                .smsConfig(objectMapper.createObjectNode())
                .termConfig(objectMapper.createObjectNode())
                .isActive(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return schoolRepository.save(school).block();
    }

    private AcademicSession seedSession(UUID schoolId) {
        AcademicSession session = AcademicSession.builder()
                .schoolId(schoolId)
                .name("2025/2026 Academic Year")
                .startDate(LocalDate.now().minusMonths(3))
                .endDate(LocalDate.now().plusMonths(3))
                .isCurrent(true)
                .status("ACTIVE")
                .build();
        return sessionRepository.save(session).block();
    }

    private Term seedTerm(UUID sessionId) {
        Term term = Term.builder()
                .sessionId(sessionId)
                .name("First Term")
                .termNumber((short) 1)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(2))
                .isCurrent(true)
                .status("ACTIVE")
                .build();
        return termRepository.save(term).block();
    }

    private ClassEntity seedClass(UUID schoolId, UUID sessionId, String name, String gradeLevel, boolean active) {
        ClassEntity cls = ClassEntity.builder()
                .schoolId(schoolId)
                .name(name)
                .gradeLevel(gradeLevel)
                .section("A")
                .academicSessionId(sessionId)
                .capacity(30)
                .isActive(active)
                .build();
        return classRepository.save(cls).block();
    }

    private UUID seedFeeCategory(UUID schoolId, String name) {
        UUID categoryId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.fee_categories (id, school_id, name, is_recurring, is_optional)
                VALUES (:categoryId, :schoolId, :name, false, false)
                """)
                .bind("categoryId", categoryId)
                .bind("schoolId", schoolId)
                .bind("name", name)
                .fetch()
                .rowsUpdated()
                .block();
        return categoryId;
    }

    private UUID seedFeeStructure(UUID schoolId, UUID sessionId, UUID termId, UUID createdBy) {
        UUID structureId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.fee_structures (
                    id,
                    school_id,
                    name,
                    academic_session_id,
                    term_id,
                    total_amount,
                    due_date,
                    status,
                    created_by
                )
                VALUES (
                    :structureId,
                    :schoolId,
                    'Primary 1 Tuition',
                    :sessionId,
                    :termId,
                    15000,
                    :dueDate,
                    'ACTIVE',
                    :createdBy
                )
                """)
                .bind("structureId", structureId)
                .bind("schoolId", schoolId)
                .bind("sessionId", sessionId)
                .bind("termId", termId)
                .bind("dueDate", LocalDate.now().plusDays(30))
                .bind("createdBy", createdBy)
                .fetch()
                .rowsUpdated()
                .block();
        return structureId;
    }

    private void seedFeeStructureItem(UUID structureId, UUID categoryId) {
        databaseClient.sql("""
                INSERT INTO fee.fee_structure_items (
                    id,
                    fee_structure_id,
                    fee_category_id,
                    description,
                    amount,
                    is_mandatory,
                    sort_order
                )
                VALUES (
                    :itemId,
                    :structureId,
                    :categoryId,
                    'Tuition',
                    15000,
                    true,
                    1
                )
                """)
                .bind("itemId", UUID.randomUUID())
                .bind("structureId", structureId)
                .bind("categoryId", categoryId)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedFeeStructureClass(UUID structureId, UUID classId) {
        databaseClient.sql("""
                INSERT INTO fee.fee_structure_classes (fee_structure_id, class_id)
                VALUES (:structureId, :classId)
                """)
                .bind("structureId", structureId)
                .bind("classId", classId)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedStudent(
            UUID schoolId,
            UUID classId,
            String admissionNumber,
            String firstName,
            String lastName,
            String status) {
        UUID studentId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.students (
                    id,
                    school_id,
                    admission_number,
                    first_name,
                    last_name,
                    current_class_id,
                    enrollment_date,
                    enrollment_status
                )
                VALUES (
                    :studentId,
                    :schoolId,
                    :admissionNumber,
                    :firstName,
                    :lastName,
                    :classId,
                    :enrollmentDate,
                    :status
                )
                """)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("admissionNumber", admissionNumber)
                .bind("firstName", firstName)
                .bind("lastName", lastName)
                .bind("classId", classId)
                .bind("enrollmentDate", LocalDate.now())
                .bind("status", status)
                .fetch()
                .rowsUpdated()
                .block();
        return studentId;
    }

    private void seedAuthUser(UUID schoolId, UUID userId) {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id,
                    keycloak_id,
                    school_id,
                    email,
                    phone,
                    first_name,
                    last_name,
                    user_type,
                    is_active
                )
                VALUES (
                    :userId,
                    :keycloakId,
                    :schoolId,
                    :email,
                    :phone,
                    'Finance',
                    'Admin',
                    'SCHOOL_ADMIN',
                    true
                )
                """)
                .bind("userId", userId)
                .bind("keycloakId", userId)
                .bind("schoolId", schoolId)
                .bind("email", "finance-" + userId + "@gis.edu")
                .bind("phone", "2348031234567")
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID findStudentFeeId(UUID structureId, UUID studentId) {
        return (UUID) databaseClient.sql("""
                SELECT id
                FROM fee.student_fees
                WHERE fee_structure_id = :structureId
                  AND student_id = :studentId
                """)
                .bind("structureId", structureId)
                .bind("studentId", studentId)
                .fetch()
                .one()
                .block()
                .get("id");
    }

    private void seedPaymentLedgerEntry(
            UUID studentFeeId,
            UUID studentId,
            BigDecimal amount,
            BigDecimal balanceAfter) {
        databaseClient.sql("""
                INSERT INTO fee.ledger_entries (
                    id,
                    student_fee_id,
                    school_id,
                    student_id,
                    entry_type,
                    amount,
                    balance_after,
                    source_entity_type,
                    source_entity_id,
                    description,
                    transaction_date,
                    idempotency_key,
                    recorded_by
                )
                VALUES (
                    :entryId,
                    :studentFeeId,
                    :schoolId,
                    :studentId,
                    'PAYMENT',
                    :amount,
                    :balanceAfter,
                    'payment',
                    :sourceEntityId,
                    'Test payment',
                    :transactionDate,
                    :idempotencyKey,
                    :recordedBy
                )
                """)
                .bind("entryId", UUID.randomUUID())
                .bind("studentFeeId", studentFeeId)
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", studentId)
                .bind("amount", amount)
                .bind("balanceAfter", balanceAfter)
                .bind("sourceEntityId", UUID.randomUUID())
                .bind("transactionDate", Instant.now())
                .bind("idempotencyKey", UUID.randomUUID())
                .bind("recordedBy", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM outbox.outbox_events").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notifications").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notification_templates").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.ledger_entries").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.student_discounts").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.student_fees").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_structure_classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_structure_items").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_structures").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_categories").fetch().rowsUpdated().block();
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

    private long countRows(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return ((Number) spec.fetch().one().block().get("count")).longValue();
    }

    private record FeeFixture(
            UUID structureId,
            UUID termId,
            UUID classId,
            UUID firstStudentId,
            UUID secondStudentId) {
    }
}
