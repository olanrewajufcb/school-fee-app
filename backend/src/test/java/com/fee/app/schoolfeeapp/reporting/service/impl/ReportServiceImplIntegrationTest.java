package com.fee.app.schoolfeeapp.reporting.service.impl;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReportServiceImplIntegrationTest {

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
    private ReportServiceImpl reportService;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID OTHER_USER_ID = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
    private static final UUID SESSION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER_SESSION_ID = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final UUID TERM_ID = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final UUID OTHER_TERM_ID = UUID.fromString("44444444-5555-6666-7777-888888888888");
    private static final UUID OTHER_SCHOOL_TERM_ID = UUID.fromString("99999999-aaaa-bbbb-cccc-dddddddddddd");
    private static final UUID CLASS_A_ID = UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final UUID CLASS_B_ID = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

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
    @DisplayName("Should generate CSV fee collection report scoped by school, current term, class, and status")
    void shouldGenerateCsvFeeCollectionReportScopedByFilters() {
        ReportFixture fixture = seedReportFixture();

        StepVerifier.create(reportService.generateFeeCollectionReport(
                        "current", CLASS_A_ID.toString(), "completed", "csv"))
                .assertNext(buffer -> {
                    String csv = bufferToString(buffer);
                    assertThat(csv).contains(fixture.includedPaymentId().toString());
                    assertThat(csv).contains("Included tuition");
                    assertThat(csv).doesNotContain("Class B tuition");
                    assertThat(csv).doesNotContain("Pending tuition");
                    assertThat(csv).doesNotContain("Other term tuition");
                    assertThat(csv).doesNotContain("Deleted tuition");
                    assertThat(csv).doesNotContain("Other school tuition");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject explicit term that belongs to another school")
    void shouldRejectExplicitTermThatBelongsToAnotherSchool() {
        seedReportFixture();

        StepVerifier.create(reportService.generateFeeCollectionReport(
                        OTHER_SCHOOL_TERM_ID.toString(), null, null, "CSV"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TERM_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should get daily summary for completed non-deleted school payments")
    void shouldGetDailySummaryForCompletedNonDeletedSchoolPayments() {
        seedReportFixture();

        StepVerifier.create(reportService.getDailySummary(
                        LocalDate.parse("2026-06-01"),
                        LocalDate.parse("2026-06-03")))
                .assertNext(response -> {
                    assertThat(response.totalCollected()).isEqualByComparingTo("3700.00");
                    assertThat(response.totalTransactions()).isEqualTo(3);
                    assertThat(response.byPaymentMethod().get("PAYSTACK").count()).isEqualTo(2);
                    assertThat(response.byPaymentMethod().get("CASH").amount()).isEqualByComparingTo("2000.00");
                    assertThat(response.dailyBreakdown()).hasSize(3);
                    assertThat(response.dailyBreakdown().get(0).amount()).isEqualByComparingTo("3000.00");
                    assertThat(response.dailyBreakdown().get(1).amount()).isEqualByComparingTo("700.00");
                    assertThat(response.dailyBreakdown().get(2).amount()).isEqualByComparingTo("0.00");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject invalid daily summary date range")
    void shouldRejectInvalidDailySummaryDateRange() {
        StepVerifier.create(reportService.getDailySummary(
                        LocalDate.parse("2026-06-03"),
                        LocalDate.parse("2026-06-01")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_REPORT_DATE_RANGE");
                })
                .verify();
    }

    private ReportFixture seedReportFixture() {
        seedSchool(SCHOOL_ID, "GIS");
        seedSchool(OTHER_SCHOOL_ID, "OTH");
        seedUser(USER_ID, SCHOOL_ID, "report-admin@gis.edu");
        seedUser(OTHER_USER_ID, OTHER_SCHOOL_ID, "report-admin@other.edu");
        seedSession(SESSION_ID, SCHOOL_ID, true);
        seedSession(OTHER_SESSION_ID, OTHER_SCHOOL_ID, true);
        seedTerm(TERM_ID, SESSION_ID, true, "First Term");
        seedTerm(OTHER_TERM_ID, SESSION_ID, false, "Second Term");
        seedTerm(OTHER_SCHOOL_TERM_ID, OTHER_SESSION_ID, true, "Other School Term");
        seedClass(CLASS_A_ID, SCHOOL_ID, SESSION_ID, "Basic 1A");
        seedClass(CLASS_B_ID, SCHOOL_ID, SESSION_ID, "Basic 1B");
        UUID otherClassId = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb");
        seedClass(otherClassId, OTHER_SCHOOL_ID, OTHER_SESSION_ID, "Other Basic 1A");

        UUID studentA = seedStudent(SCHOOL_ID, CLASS_A_ID, "GIS-001");
        UUID studentB = seedStudent(SCHOOL_ID, CLASS_B_ID, "GIS-002");
        UUID otherStudent = seedStudent(OTHER_SCHOOL_ID, otherClassId, "OTH-001");

        UUID currentStructure = seedFeeStructure(SCHOOL_ID, SESSION_ID, TERM_ID, USER_ID, "Current Fees");
        UUID otherTermStructure = seedFeeStructure(SCHOOL_ID, SESSION_ID, OTHER_TERM_ID, USER_ID, "Other Term Fees");
        UUID otherSchoolStructure = seedFeeStructure(
                OTHER_SCHOOL_ID, OTHER_SESSION_ID, OTHER_SCHOOL_TERM_ID, OTHER_USER_ID, "Other School Fees");

        UUID studentFeeA = seedStudentFee(SCHOOL_ID, currentStructure, studentA);
        UUID studentFeeB = seedStudentFee(SCHOOL_ID, currentStructure, studentB);
        UUID otherTermStudentFee = seedStudentFee(SCHOOL_ID, otherTermStructure, studentA);
        UUID otherSchoolStudentFee = seedStudentFee(OTHER_SCHOOL_ID, otherSchoolStructure, otherStudent);

        UUID includedPaymentId = seedPayment(
                SCHOOL_ID,
                studentFeeA,
                studentA,
                BigDecimal.valueOf(1000),
                "PAYSTACK",
                "COMPLETED",
                Instant.parse("2026-06-01T09:00:00Z"),
                "Included tuition",
                null);
        seedPayment(
                SCHOOL_ID,
                studentFeeB,
                studentB,
                BigDecimal.valueOf(2000),
                "CASH",
                "COMPLETED",
                Instant.parse("2026-06-01T11:00:00Z"),
                "Class B tuition",
                null);
        seedPayment(
                SCHOOL_ID,
                studentFeeA,
                studentA,
                BigDecimal.valueOf(500),
                "PAYSTACK",
                "PENDING",
                Instant.parse("2026-06-01T12:00:00Z"),
                "Pending tuition",
                null);
        seedPayment(
                SCHOOL_ID,
                otherTermStudentFee,
                studentA,
                BigDecimal.valueOf(700),
                "PAYSTACK",
                "COMPLETED",
                Instant.parse("2026-06-02T08:00:00Z"),
                "Other term tuition",
                null);
        seedPayment(
                SCHOOL_ID,
                studentFeeA,
                studentA,
                BigDecimal.valueOf(300),
                "PAYSTACK",
                "COMPLETED",
                Instant.parse("2026-06-01T13:00:00Z"),
                "Deleted tuition",
                Instant.parse("2026-06-01T14:00:00Z"));
        seedPayment(
                OTHER_SCHOOL_ID,
                otherSchoolStudentFee,
                otherStudent,
                BigDecimal.valueOf(900),
                "PAYSTACK",
                "COMPLETED",
                Instant.parse("2026-06-01T10:00:00Z"),
                "Other school tuition",
                null);

        return new ReportFixture(includedPaymentId);
    }

    private void seedSchool(UUID schoolId, String code) {
        databaseClient.sql("""
                INSERT INTO school.schools (
                    id, name, code, email, phone, address, city, state, country,
                    payment_config, sms_config, term_config, is_active
                )
                VALUES (
                    :schoolId, :name, :code, :email, '+2348012345678', '12 School Road',
                    'Lagos', 'Lagos', 'Nigeria',
                    '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, true
                )
                """)
                .bind("schoolId", schoolId)
                .bind("name", "School " + code)
                .bind("code", code)
                .bind("email", code.toLowerCase() + "@school.test")
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedUser(UUID userId, UUID schoolId, String email) {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id, keycloak_id, school_id, email, phone, first_name,
                    last_name, user_type, is_active
                )
                VALUES (
                    :userId, :keycloakId, :schoolId, :email, '+2348012345678',
                    'Report', 'Admin', 'SCHOOL_ADMIN', true
                )
                """)
                .bind("userId", userId)
                .bind("keycloakId", UUID.randomUUID())
                .bind("schoolId", schoolId)
                .bind("email", email)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedSession(UUID sessionId, UUID schoolId, boolean isCurrent) {
        databaseClient.sql("""
                INSERT INTO school.academic_sessions (
                    id, school_id, name, start_date, end_date, is_current
                )
                VALUES (
                    :sessionId, :schoolId, :name, :startDate, :endDate, :isCurrent
                )
                """)
                .bind("sessionId", sessionId)
                .bind("schoolId", schoolId)
                .bind("name", "2025/2026")
                .bind("startDate", LocalDate.parse("2025-09-01"))
                .bind("endDate", LocalDate.parse("2026-07-31"))
                .bind("isCurrent", isCurrent)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedTerm(UUID termId, UUID sessionId, boolean isCurrent, String name) {
        databaseClient.sql("""
                INSERT INTO school.terms (
                    id, session_id, name, term_number, start_date, end_date, is_current
                )
                VALUES (
                    :termId, :sessionId, :name, 1, :startDate, :endDate, :isCurrent
                )
                """)
                .bind("termId", termId)
                .bind("sessionId", sessionId)
                .bind("name", name)
                .bind("startDate", LocalDate.parse("2026-01-10"))
                .bind("endDate", LocalDate.parse("2026-04-10"))
                .bind("isCurrent", isCurrent)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedClass(UUID classId, UUID schoolId, UUID sessionId, String name) {
        databaseClient.sql("""
                INSERT INTO school.classes (
                    id, school_id, name, grade_level, academic_session_id, capacity, is_active
                )
                VALUES (
                    :classId, :schoolId, :name, 'Basic 1', :sessionId, 40, true
                )
                """)
                .bind("classId", classId)
                .bind("schoolId", schoolId)
                .bind("name", name)
                .bind("sessionId", sessionId)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedStudent(UUID schoolId, UUID classId, String admissionNumber) {
        UUID studentId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.students (
                    id, school_id, admission_number, first_name, last_name,
                    current_class_id, enrollment_date, enrollment_status
                )
                VALUES (
                    :studentId, :schoolId, :admissionNumber, 'Test', 'Student',
                    :classId, :enrollmentDate, 'ACTIVE'
                )
                """)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("admissionNumber", admissionNumber)
                .bind("classId", classId)
                .bind("enrollmentDate", LocalDate.parse("2025-09-01"))
                .fetch()
                .rowsUpdated()
                .block();
        return studentId;
    }

    private UUID seedFeeStructure(
            UUID schoolId, UUID sessionId, UUID termId, UUID createdBy, String name) {
        UUID structureId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.fee_structures (
                    id, school_id, name, academic_session_id, term_id, total_amount,
                    due_date, status, created_by
                )
                VALUES (
                    :structureId, :schoolId, :name, :sessionId, :termId, 10000,
                    :dueDate, 'ACTIVE', :createdBy
                )
                """)
                .bind("structureId", structureId)
                .bind("schoolId", schoolId)
                .bind("name", name)
                .bind("sessionId", sessionId)
                .bind("termId", termId)
                .bind("dueDate", LocalDate.parse("2026-02-15"))
                .bind("createdBy", createdBy)
                .fetch()
                .rowsUpdated()
                .block();
        return structureId;
    }

    private UUID seedStudentFee(UUID schoolId, UUID structureId, UUID studentId) {
        UUID studentFeeId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.student_fees (
                    id, fee_structure_id, student_id, school_id, total_amount, due_date
                )
                VALUES (
                    :studentFeeId, :structureId, :studentId, :schoolId, 10000, :dueDate
                )
                """)
                .bind("studentFeeId", studentFeeId)
                .bind("structureId", structureId)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("dueDate", LocalDate.parse("2026-02-15"))
                .fetch()
                .rowsUpdated()
                .block();
        return studentFeeId;
    }

    private UUID seedPayment(
            UUID schoolId,
            UUID studentFeeId,
            UUID studentId,
            BigDecimal amount,
            String paymentMethod,
            String status,
            Instant createdAt,
            String narration,
            Instant deletedAt) {
        UUID paymentId = UUID.randomUUID();
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                INSERT INTO payment.payments (
                    id, student_fee_id, student_id, school_id, amount,
                    payment_method, payment_gateway, gateway_transaction_ref,
                    payment_mode, status, paid_by, payer_phone, narration,
                    idempotency_key, created_at, updated_at, deleted_at
                )
                VALUES (
                    :paymentId, :studentFeeId, :studentId, :schoolId, :amount,
                    :paymentMethod, 'PAYSTACK', :gatewayRef,
                    'ONLINE', :status, :paidBy, '+2348012345678', :narration,
                    :idempotencyKey, :createdAt, :updatedAt, :deletedAt
                )
                """)
                .bind("paymentId", paymentId)
                .bind("studentFeeId", studentFeeId)
                .bind("studentId", studentId)
                .bind("schoolId", schoolId)
                .bind("amount", amount)
                .bind("paymentMethod", paymentMethod)
                .bind("gatewayRef", "report-" + paymentId)
                .bind("status", status)
                .bind("paidBy", schoolId.equals(SCHOOL_ID) ? USER_ID : OTHER_USER_ID)
                .bind("narration", narration)
                .bind("idempotencyKey", "report-" + paymentId)
                .bind("createdAt", createdAt)
                .bind("updatedAt", createdAt);
        if (deletedAt == null) {
            spec = spec.bindNull("deletedAt", Instant.class);
        } else {
            spec = spec.bind("deletedAt", deletedAt);
        }
        spec.fetch().rowsUpdated().block();
        return paymentId;
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM outbox.outbox_events").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notifications").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notification_templates").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM payment.receipts").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM payment.payment_allocations").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM payment.payments").fetch().rowsUpdated().block();
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

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .email("report-admin@gis.edu")
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }

    private String bufferToString(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private record ReportFixture(UUID includedPaymentId) {
    }
}
