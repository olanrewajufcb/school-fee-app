package com.fee.app.schoolfeeapp.reporting.controller;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
import java.time.Instant;
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
class ReportControllerIntegrationTest {

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
    private static final UUID CLASS_A_ID = UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final UUID CLASS_B_ID = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Test
    @DisplayName("Should download fee collection CSV report through controller")
    void shouldDownloadFeeCollectionCsvReportThroughController() {
        seedReportFixture();

        authenticatedClient("ACCOUNTANT", "ACCOUNTANT")
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reports/fee-collection")
                        .queryParam("termId", "current")
                        .queryParam("classId", CLASS_A_ID)
                        .queryParam("status", "completed")
                        .queryParam("format", "csv")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/csv")
                .expectHeader().valueEquals(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"fee-collection-report.csv\"")
                .expectBody(String.class)
                .consumeWith(response -> {
                    String csv = response.getResponseBody();
                    assertThat(csv).contains("Included tuition");
                    assertThat(csv).doesNotContain("Class B tuition");
                    assertThat(csv).doesNotContain("Pending tuition");
                });
    }

    @Test
    @DisplayName("Should get daily summary through controller")
    void shouldGetDailySummaryThroughController() {
        seedReportFixture();

        authenticatedClient("SCHOOL_ADMIN", "SCHOOL_ADMIN")
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reports/daily-summary")
                        .queryParam("startDate", "2026-06-01")
                        .queryParam("endDate", "2026-06-02")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.totalCollected").isEqualTo(3000.00)
                .jsonPath("$.data.totalTransactions").isEqualTo(2)
                .jsonPath("$.data.byPaymentMethod.PAYSTACK.count").isEqualTo(1)
                .jsonPath("$.data.byPaymentMethod.CASH.amount").isEqualTo(2000.00)
                .jsonPath("$.data.dailyBreakdown.length()").isEqualTo(2)
                .jsonPath("$.data.dailyBreakdown[1].amount").isEqualTo(0);
    }

    @Test
    @DisplayName("Should reject invalid daily summary date range through controller")
    void shouldRejectInvalidDailySummaryDateRangeThroughController() {
        authenticatedClient("SCHOOL_ADMIN", "SCHOOL_ADMIN")
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reports/daily-summary")
                        .queryParam("startDate", "2026-06-03")
                        .queryParam("endDate", "2026-06-01")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errors[0].code").isEqualTo("INVALID_REPORT_DATE_RANGE");
    }

    @Test
    @DisplayName("Should forbid parent from report endpoints")
    void shouldForbidParentFromReportEndpoints() {
        authenticatedClient("PARENT", "PARENT")
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reports/daily-summary")
                        .queryParam("startDate", "2026-06-01")
                        .queryParam("endDate", "2026-06-01")
                        .build())
                .exchange()
                .expectStatus().isForbidden();
    }

    private WebTestClient authenticatedClient(String userType, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID.toString())
                        .claim("preferred_username", "report-admin")
                        .claim("email", "report-admin@gis.edu")
                        .claim("given_name", "Report")
                        .claim("family_name", "Admin")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", SCHOOL_ID.toString())
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private void seedReportFixture() {
        seedSchool();
        seedUser();
        seedSession();
        seedTerm();
        seedClass(CLASS_A_ID, "Basic 1A");
        seedClass(CLASS_B_ID, "Basic 1B");
        UUID studentA = seedStudent(CLASS_A_ID, "GIS-001");
        UUID studentB = seedStudent(CLASS_B_ID, "GIS-002");
        UUID structureId = seedFeeStructure();
        UUID studentFeeA = seedStudentFee(structureId, studentA);
        UUID studentFeeB = seedStudentFee(structureId, studentB);

        seedPayment(
                studentFeeA,
                studentA,
                BigDecimal.valueOf(1000),
                "PAYSTACK",
                "COMPLETED",
                Instant.parse("2026-06-01T09:00:00Z"),
                "Included tuition");
        seedPayment(
                studentFeeB,
                studentB,
                BigDecimal.valueOf(2000),
                "CASH",
                "COMPLETED",
                Instant.parse("2026-06-01T11:00:00Z"),
                "Class B tuition");
        seedPayment(
                studentFeeA,
                studentA,
                BigDecimal.valueOf(500),
                "PAYSTACK",
                "PENDING",
                Instant.parse("2026-06-01T12:00:00Z"),
                "Pending tuition");
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

    private void seedUser() {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id, keycloak_id, school_id, email, phone, first_name,
                    last_name, user_type, is_active
                )
                VALUES (
                    :userId, :keycloakId, :schoolId, 'report-admin@gis.edu',
                    '+2348012345678', 'Report', 'Admin', 'SCHOOL_ADMIN', true
                )
                """)
                .bind("userId", USER_ID)
                .bind("keycloakId", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
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

    private void seedClass(UUID classId, String name) {
        databaseClient.sql("""
                INSERT INTO school.classes (
                    id, school_id, name, grade_level, academic_session_id, capacity, is_active
                )
                VALUES (
                    :classId, :schoolId, :name, 'Basic 1', :sessionId, 40, true
                )
                """)
                .bind("classId", classId)
                .bind("schoolId", SCHOOL_ID)
                .bind("name", name)
                .bind("sessionId", SESSION_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedStudent(UUID classId, String admissionNumber) {
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
                .bind("schoolId", SCHOOL_ID)
                .bind("admissionNumber", admissionNumber)
                .bind("classId", classId)
                .bind("enrollmentDate", LocalDate.parse("2025-09-01"))
                .fetch()
                .rowsUpdated()
                .block();
        return studentId;
    }

    private UUID seedFeeStructure() {
        UUID structureId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.fee_structures (
                    id, school_id, name, academic_session_id, term_id, total_amount,
                    due_date, status, created_by
                )
                VALUES (
                    :structureId, :schoolId, 'Current Fees', :sessionId, :termId, 10000,
                    :dueDate, 'ACTIVE', :createdBy
                )
                """)
                .bind("structureId", structureId)
                .bind("schoolId", SCHOOL_ID)
                .bind("sessionId", SESSION_ID)
                .bind("termId", TERM_ID)
                .bind("dueDate", LocalDate.parse("2026-02-15"))
                .bind("createdBy", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
        return structureId;
    }

    private UUID seedStudentFee(UUID structureId, UUID studentId) {
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
                .bind("schoolId", SCHOOL_ID)
                .bind("dueDate", LocalDate.parse("2026-02-15"))
                .fetch()
                .rowsUpdated()
                .block();
        return studentFeeId;
    }

    private void seedPayment(
            UUID studentFeeId,
            UUID studentId,
            BigDecimal amount,
            String paymentMethod,
            String status,
            Instant createdAt,
            String narration) {
        UUID paymentId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO payment.payments (
                    id, student_fee_id, student_id, school_id, amount,
                    payment_method, payment_gateway, gateway_transaction_ref,
                    payment_mode, status, paid_by, payer_phone, narration,
                    idempotency_key, created_at, updated_at
                )
                VALUES (
                    :paymentId, :studentFeeId, :studentId, :schoolId, :amount,
                    :paymentMethod, 'PAYSTACK', :gatewayRef,
                    'ONLINE', :status, :paidBy, '+2348012345678', :narration,
                    :idempotencyKey, :createdAt, :updatedAt
                )
                """)
                .bind("paymentId", paymentId)
                .bind("studentFeeId", studentFeeId)
                .bind("studentId", studentId)
                .bind("schoolId", SCHOOL_ID)
                .bind("amount", amount)
                .bind("paymentMethod", paymentMethod)
                .bind("gatewayRef", "report-controller-" + paymentId)
                .bind("status", status)
                .bind("paidBy", USER_ID)
                .bind("narration", narration)
                .bind("idempotencyKey", "report-controller-" + paymentId)
                .bind("createdAt", createdAt)
                .bind("updatedAt", createdAt)
                .fetch()
                .rowsUpdated()
                .block();
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
}
