package com.fee.app.schoolfeeapp.fee.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.dto.request.CreateFeeStructureRequest;
import com.fee.app.schoolfeeapp.fee.dto.response.FeeAssignmentResponse;
import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.domain.Term;
import com.fee.app.schoolfeeapp.school.repository.AcademicSessionRepository;
import com.fee.app.schoolfeeapp.school.repository.ClassRepository;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.school.repository.TermRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class FeeServiceImplIntegrationTest {

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
    private FeeServiceImpl feeService;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private AcademicSessionRepository sessionRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

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

    @Nested
    @DisplayName("Create Fee Structure - Service Integration Tests")
    class CreateFeeStructureIntegrationTests {

        @Test
        @DisplayName("Should create fee structure with items and class links")
        void shouldCreateFeeStructureWithItemsAndClassLinks() {
            School school = seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedAuthUser(school.getId(), USER_ID);
            AcademicSession session = seedSession(school.getId());
            Term term = seedTerm(session.getId());
            ClassEntity cls = seedClass(school.getId(), session.getId(), "Primary 1", "PRIMARY_1", true);
            UUID categoryId = seedFeeCategory(school.getId(), "Tuition");
            seedStudent(school.getId(), cls.getId(), "STU260001", "Ada", "Lovelace", "ACTIVE");
            seedStudent(school.getId(), cls.getId(), "STU260002", "Katherine", "Johnson", "ACTIVE");
            seedStudent(school.getId(), cls.getId(), "STU260003", "Inactive", "Student", "WITHDRAWN");

            StepVerifier.create(feeService.createFeeStructure(validRequest(session.getId(), term.getId(), cls.getId(), categoryId)))
                    .assertNext(response -> {
                        assertThat(response.structureId()).isNotNull();
                        assertThat(response.name()).isEqualTo("Primary 1 Tuition");
                        assertThat(response.totalAmount()).isEqualByComparingTo("15000");
                        assertThat(response.mandatoryAmount()).isEqualByComparingTo("10000");
                        assertThat(response.applicableClassCount()).isEqualTo(1);
                        assertThat(response.estimatedStudentCount()).isEqualTo(2);
                    })
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.fee_structures
                    WHERE school_id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.fee_structure_items
                    """, Map.of()))
                    .isEqualTo(2);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.fee_structure_classes
                    WHERE class_id = :classId
                    """, Map.of("classId", cls.getId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject duplicate active structure name for same term")
        void shouldRejectDuplicateActiveStructureNameForSameTerm() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true);
            seedAuthUser(SCHOOL_ID, USER_ID);
            AcademicSession session = seedSession(SCHOOL_ID);
            Term term = seedTerm(session.getId());
            ClassEntity cls = seedClass(SCHOOL_ID, session.getId(), "Primary 1", "PRIMARY_1", true);
            UUID categoryId = seedFeeCategory(SCHOOL_ID, "Tuition");
            CreateFeeStructureRequest request = validRequest(session.getId(), term.getId(), cls.getId(), categoryId);

            feeService.createFeeStructure(request).block(Duration.ofSeconds(5));

            StepVerifier.create(feeService.createFeeStructure(request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("DUPLICATE_FEE_STRUCTURE");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("Assign Fees - Service Integration Tests")
    class AssignFeesIntegrationTests {

        @Test
        @DisplayName("Should assign fees idempotently")
        void shouldAssignFeesIdempotently() {
            FeeFixture fixture = seedFeeFixture();

            StepVerifier.create(feeService.assignFeesToStudents(fixture.structureId()))
                    .assertNext(response -> {
                        assertThat(response.structureId()).isEqualTo(fixture.structureId());
                        assertThat(response.studentsAssigned()).isEqualTo(2);
                        assertThat(response.totalExpectedAmount()).isEqualByComparingTo("30000");
                    })
                    .verifyComplete();

            StepVerifier.create(feeService.assignFeesToStudents(fixture.structureId()))
                    .assertNext(response -> assertThat(response.studentsAssigned()).isZero())
                    .verifyComplete();

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
        @DisplayName("Should serialize concurrent assignments and avoid duplicates")
        void shouldSerializeConcurrentAssignmentsAndAvoidDuplicates() throws Exception {
            FeeFixture fixture = seedFeeFixture();

            CompletableFuture<Object> firstAssignment = assignAsync(fixture.structureId());
            CompletableFuture<Object> secondAssignment = assignAsync(fixture.structureId());

            Object firstResult = firstAssignment.get(10, TimeUnit.SECONDS);
            Object secondResult = secondAssignment.get(10, TimeUnit.SECONDS);

            List<Object> results = List.of(firstResult, secondResult);
            assertThat(results.stream()
                    .filter(result -> result instanceof FeeAssignmentResponse response
                            && response.studentsAssigned() == 2)
                    .count())
                    .isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> result instanceof FeeAssignmentResponse response
                            && response.studentsAssigned() == 0)
                    .count())
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.student_fees
                    WHERE fee_structure_id = :structureId
                    """, Map.of("structureId", fixture.structureId())))
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Remaining Fee Service Read Methods - Integration Tests")
    class ReadMethodIntegrationTests {

        @Test
        @DisplayName("Should list enriched fee structures")
        void shouldListEnrichedFeeStructures() {
            FeeFixture fixture = seedFeeFixture();
            feeService.assignFeesToStudents(fixture.structureId()).block(Duration.ofSeconds(5));
            UUID studentFeeId = findStudentFeeId(fixture.structureId(), fixture.firstStudentId());
            seedPaymentLedgerEntry(
                    studentFeeId,
                    fixture.firstStudentId(),
                    BigDecimal.valueOf(-15000),
                    BigDecimal.ZERO);

            StepVerifier.create(feeService.getFeeStructures("ACTIVE", "current"))
                    .assertNext(responses -> {
                        assertThat(responses).hasSize(1);
                        var response = responses.getFirst();
                        assertThat(response.structureId()).isEqualTo(fixture.structureId());
                        assertThat(response.termName()).isEqualTo("First Term");
                        assertThat(response.sessionName()).isEqualTo("2025/2026");
                        assertThat(response.mandatoryAmount()).isEqualByComparingTo("10000");
                        assertThat(response.applicableToClasses()).containsExactly("Primary 1");
                        assertThat(response.studentCount()).isEqualTo(2);
                        assertThat(response.collectionRate()).isEqualTo(50.0);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return student fees with live payment balance")
        void shouldReturnStudentFeesWithLivePaymentBalance() {
            FeeFixture fixture = seedFeeFixture();
            feeService.assignFeesToStudents(fixture.structureId()).block(Duration.ofSeconds(5));
            UUID studentFeeId = findStudentFeeId(fixture.structureId(), fixture.firstStudentId());
            seedPaymentLedgerEntry(
                    studentFeeId,
                    fixture.firstStudentId(),
                    BigDecimal.valueOf(-5000),
                    BigDecimal.valueOf(10000));

            StepVerifier.create(feeService.getStudentFees(fixture.firstStudentId()))
                    .assertNext(responses -> {
                        assertThat(responses).hasSize(1);
                        var response = responses.getFirst();
                        assertThat(response.studentFeeId()).isEqualTo(studentFeeId);
                        assertThat(response.structureName()).isEqualTo("Primary 1 Tuition");
                        assertThat(response.termName()).isEqualTo("First Term");
                        assertThat(response.items()).hasSize(2);
                        assertThat(response.amountPaid()).isEqualByComparingTo("5000");
                        assertThat(response.balance()).isEqualByComparingTo("10000");
                        assertThat(response.status()).isEqualTo("PARTIAL");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject parent without fee access")
        void shouldRejectParentWithoutFeeAccess() {
            FeeFixture fixture = seedFeeFixture();
            feeService.assignFeesToStudents(fixture.structureId()).block(Duration.ofSeconds(5));
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

            StepVerifier.create(feeService.getStudentFees(fixture.firstStudentId()))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should return dashboard with live ledger totals")
        void shouldReturnDashboardWithLiveLedgerTotals() {
            FeeFixture fixture = seedFeeFixture();
            feeService.assignFeesToStudents(fixture.structureId()).block(Duration.ofSeconds(5));
            UUID studentFeeId = findStudentFeeId(fixture.structureId(), fixture.firstStudentId());
            seedPaymentLedgerEntry(
                    studentFeeId,
                    fixture.firstStudentId(),
                    BigDecimal.valueOf(-15000),
                    BigDecimal.ZERO);

            StepVerifier.create(feeService.getFeeDashboard("current"))
                    .assertNext(response -> {
                        assertThat(response.termName()).isEqualTo("First Term");
                        assertThat(response.summary().totalExpected()).isEqualByComparingTo("30000");
                        assertThat(response.summary().totalCollected()).isEqualByComparingTo("15000");
                        assertThat(response.summary().totalOutstanding()).isEqualByComparingTo("15000");
                        assertThat(response.summary().collectionRate()).isEqualTo(50.0);
                        assertThat(response.summary().fullyPaidStudents()).isEqualTo(1);
                        assertThat(response.summary().unpaidStudents()).isEqualTo(1);
                        assertThat(response.byClass()).hasSize(1);
                        assertThat(response.byClass().getFirst().className()).isEqualTo("Primary 1");
                        assertThat(response.dailyCollectionTrend()).hasSize(1);
                    })
                    .verifyComplete();
        }
    }

    private CompletableFuture<Object> assignAsync(UUID structureId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return feeService.assignFeesToStudents(structureId).block(Duration.ofSeconds(8));
            } catch (Throwable error) {
                return error;
            }
        });
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
        CreateFeeStructureRequest request = validRequest(session.getId(), term.getId(), cls.getId(), categoryId);
        UUID structureId = feeService.createFeeStructure(request)
                .block(Duration.ofSeconds(5))
                .structureId();
        return new FeeFixture(structureId, term.getId(), cls.getId(), firstStudentId, secondStudentId);
    }

    private CreateFeeStructureRequest validRequest(UUID sessionId, UUID termId, UUID classId, UUID categoryId) {
        return new CreateFeeStructureRequest(
                " Primary 1 Tuition ",
                sessionId,
                termId,
                List.of(classId, classId),
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

    private ClassEntity seedClass(
            UUID schoolId,
            UUID sessionId,
            String name,
            String gradeLevel,
            boolean active) {
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

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .email("finance@gis.edu")
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

    private record FeeFixture(
            UUID structureId,
            UUID termId,
            UUID classId,
            UUID firstStudentId,
            UUID secondStudentId) {
    }
}
