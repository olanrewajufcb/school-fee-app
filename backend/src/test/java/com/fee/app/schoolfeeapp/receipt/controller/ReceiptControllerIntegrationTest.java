package com.fee.app.schoolfeeapp.receipt.controller;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.nio.charset.StandardCharsets;
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
class ReceiptControllerIntegrationTest {

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
    private static final UUID PARENT_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID OTHER_PARENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String RECEIPT_NUMBER = "RCP-2026-ABC123";

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Test
    @DisplayName("Should get receipt details for authenticated parent")
    void shouldGetReceiptDetailsForAuthenticatedParent() {
        seedReceiptFixture(PARENT_USER_ID, RECEIPT_NUMBER);

        authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                .get()
                .uri("/api/v1/receipts/{receiptNumber}", RECEIPT_NUMBER)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.receiptNumber").isEqualTo(RECEIPT_NUMBER)
                .jsonPath("$.data.schoolName").isEqualTo("Grace International School")
                .jsonPath("$.data.paidBy").isEqualTo("Ada Parent")
                .jsonPath("$.data.amount").isEqualTo(5000)
                .jsonPath("$.data.paymentMethod").isEqualTo("PAYSTACK")
                .jsonPath("$.data.breakdown[0].studentName").isEqualTo("Ada Lovelace")
                .jsonPath("$.data.breakdown[0].amount").isEqualTo(5000);
    }

    @Test
    @DisplayName("Should download receipt PDF for authenticated parent")
    void shouldDownloadReceiptPdfForAuthenticatedParent() {
        seedReceiptFixture(PARENT_USER_ID, RECEIPT_NUMBER);

        byte[] body = authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                .get()
                .uri("/api/v1/receipts/{receiptNumber}/pdf", RECEIPT_NUMBER)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/pdf")
                .expectHeader().valueMatches("Content-Disposition", ".*receipt-RCP-2026-ABC123\\.pdf.*")
                .expectBody(byte[].class)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Should reject parent reading another payer receipt")
    void shouldRejectParentReadingAnotherPayerReceipt() {
        seedReceiptFixture(OTHER_PARENT_ID, RECEIPT_NUMBER);

        authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                .get()
                .uri("/api/v1/receipts/{receiptNumber}", RECEIPT_NUMBER)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errors[0].code").isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("Should allow school accountant to download school receipt")
    void shouldAllowSchoolAccountantToDownloadSchoolReceipt() {
        seedReceiptFixture(PARENT_USER_ID, RECEIPT_NUMBER);

        authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                .get()
                .uri("/api/v1/receipts/{receiptNumber}/pdf", RECEIPT_NUMBER)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/pdf")
                .expectBody(byte[].class)
                .consumeWith(result -> assertThat(result.getResponseBody()).isNotEmpty());
    }

    private WebTestClient authenticatedClient(UUID schoolId, String userType, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(PARENT_USER_ID.toString())
                        .claim("preferred_username", "testparent")
                        .claim("email", "parent@gis.edu")
                        .claim("given_name", "Parent")
                        .claim("family_name", "User")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", schoolId != null ? schoolId.toString() : "*")
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private PaymentFixture seedReceiptFixture(UUID paidBy, String receiptNumber) {
        seedSchool();
        seedAuthUser(PARENT_USER_ID, "PARENT", "parent@gis.edu");
        if (!PARENT_USER_ID.equals(paidBy)) {
            seedAuthUser(paidBy, "PARENT", "other-parent@gis.edu");
        }
        UUID sessionId = seedSession();
        UUID classId = seedClass(sessionId);
        UUID studentId = seedStudent(classId);
        seedGuardianLink(paidBy, studentId);
        UUID structureId = seedFeeStructure(sessionId);
        UUID studentFeeId = seedStudentFee(structureId, studentId);
        UUID paymentId = seedPayment(studentFeeId, studentId, paidBy);
        seedPaymentAllocation(paymentId, studentFeeId, BigDecimal.valueOf(5000));
        seedReceipt(paymentId, studentId, paidBy, receiptNumber);
        return new PaymentFixture(paymentId, studentId, studentFeeId);
    }

    private void seedGuardianLink(UUID userId, UUID studentId) {
        UUID guardianId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.student_guardians (
                    id, school_id, first_name, last_name, phone, user_id, is_active
                )
                VALUES (
                    :guardianId, :schoolId, 'Parent', 'User', '2348031234567', :userId, true
                )
                """)
                .bind("guardianId", guardianId)
                .bind("schoolId", SCHOOL_ID)
                .bind("userId", userId)
                .fetch()
                .rowsUpdated()
                .block();
        databaseClient.sql("""
                INSERT INTO school.student_guardian_links (
                    id, guardian_id, student_id, school_id, relationship,
                    is_primary_contact, can_view_fees, contact_priority
                )
                VALUES (
                    :linkId, :guardianId, :studentId, :schoolId, 'MOTHER',
                    true, true, 1
                )
                """)
                .bind("linkId", UUID.randomUUID())
                .bind("guardianId", guardianId)
                .bind("studentId", studentId)
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
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

    private void seedAuthUser(UUID userId, String userType, String email) {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id, keycloak_id, school_id, email, phone, first_name, last_name, user_type, is_active
                )
                VALUES (
                    :userId, :keycloakId, :schoolId, :email, :phone,
                    'Ada', 'Parent', :userType, true
                )
                """)
                .bind("userId", userId)
                .bind("keycloakId", userId)
                .bind("schoolId", SCHOOL_ID)
                .bind("email", email)
                .bind("phone", "2348" + Math.abs(userId.hashCode()))
                .bind("userType", userType)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedSession() {
        UUID sessionId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.academic_sessions (
                    id, school_id, name, start_date, end_date, is_current, status
                )
                VALUES (
                    :sessionId, :schoolId, '2025/2026 Academic Year',
                    :startDate, :endDate, true, 'ACTIVE'
                )
                """)
                .bind("sessionId", sessionId)
                .bind("schoolId", SCHOOL_ID)
                .bind("startDate", LocalDate.now().minusMonths(3))
                .bind("endDate", LocalDate.now().plusMonths(3))
                .fetch()
                .rowsUpdated()
                .block();
        return sessionId;
    }

    private UUID seedClass(UUID sessionId) {
        UUID classId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.classes (
                    id, school_id, name, grade_level, section, academic_session_id, capacity, is_active
                )
                VALUES (
                    :classId, :schoolId, 'Primary 1', 'PRIMARY_1', 'A', :sessionId, 30, true
                )
                """)
                .bind("classId", classId)
                .bind("schoolId", SCHOOL_ID)
                .bind("sessionId", sessionId)
                .fetch()
                .rowsUpdated()
                .block();
        return classId;
    }

    private UUID seedStudent(UUID classId) {
        UUID studentId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.students (
                    id, school_id, admission_number, first_name, last_name,
                    current_class_id, enrollment_date, enrollment_status
                )
                VALUES (
                    :studentId, :schoolId, 'STU260010', 'Ada', 'Lovelace',
                    :classId, :enrollmentDate, 'ACTIVE'
                )
                """)
                .bind("studentId", studentId)
                .bind("schoolId", SCHOOL_ID)
                .bind("classId", classId)
                .bind("enrollmentDate", LocalDate.now())
                .fetch()
                .rowsUpdated()
                .block();
        return studentId;
    }

    private UUID seedFeeStructure(UUID sessionId) {
        UUID structureId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.fee_structures (
                    id, school_id, name, academic_session_id,
                    total_amount, due_date, status, created_by
                )
                VALUES (
                    :structureId, :schoolId, 'Primary 1 Tuition',
                    :sessionId, 10000, :dueDate, 'ACTIVE', :createdBy
                )
                """)
                .bind("structureId", structureId)
                .bind("schoolId", SCHOOL_ID)
                .bind("sessionId", sessionId)
                .bind("dueDate", LocalDate.now().plusDays(30))
                .bind("createdBy", PARENT_USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
        return structureId;
    }

    private UUID seedStudentFee(UUID structureId, UUID studentId) {
        UUID studentFeeId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.student_fees (
                    id, fee_structure_id, student_id, school_id,
                    total_amount, discount_amount, due_date
                )
                VALUES (
                    :studentFeeId, :structureId, :studentId, :schoolId,
                    10000, 0, :dueDate
                )
                """)
                .bind("studentFeeId", studentFeeId)
                .bind("structureId", structureId)
                .bind("studentId", studentId)
                .bind("schoolId", SCHOOL_ID)
                .bind("dueDate", LocalDate.now().plusDays(30))
                .fetch()
                .rowsUpdated()
                .block();
        return studentFeeId;
    }

    private UUID seedPayment(UUID studentFeeId, UUID studentId, UUID paidBy) {
        UUID paymentId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO payment.payments (
                    id, student_fee_id, student_id, school_id, amount,
                    payment_method, payment_gateway, gateway_transaction_ref, gateway_status,
                    payment_mode, status, paid_by, payer_phone, payer_name, idempotency_key,
                    created_at, updated_at
                )
                VALUES (
                    :paymentId, :studentFeeId, :studentId, :schoolId, 5000,
                    'PAYSTACK', 'PAYSTACK', :gatewayRef, 'SUCCESS',
                    'ONLINE', 'COMPLETED', :paidBy, '08012345678', 'Ada Parent', :idempotencyKey,
                    :createdAt, :updatedAt
                )
                """)
                .bind("paymentId", paymentId)
                .bind("studentFeeId", studentFeeId)
                .bind("studentId", studentId)
                .bind("schoolId", SCHOOL_ID)
                .bind("gatewayRef", "paystack-" + paymentId)
                .bind("paidBy", paidBy)
                .bind("idempotencyKey", UUID.randomUUID().toString())
                .bind("createdAt", Instant.parse("2026-06-05T09:55:00Z"))
                .bind("updatedAt", Instant.parse("2026-06-05T10:00:00Z"))
                .fetch()
                .rowsUpdated()
                .block();
        return paymentId;
    }

    private void seedPaymentAllocation(UUID paymentId, UUID studentFeeId, BigDecimal amount) {
        databaseClient.sql("""
                INSERT INTO payment.payment_allocations (
                    id, school_id, payment_id, student_fee_id, amount
                )
                VALUES (
                    :allocationId, :schoolId, :paymentId, :studentFeeId, :amount
                )
                """)
                .bind("allocationId", UUID.randomUUID())
                .bind("schoolId", SCHOOL_ID)
                .bind("paymentId", paymentId)
                .bind("studentFeeId", studentFeeId)
                .bind("amount", amount)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedReceipt(UUID paymentId, UUID studentId, UUID paidBy, String receiptNumber) {
        databaseClient.sql("""
                INSERT INTO payment.receipts (
                    id, payment_id, receipt_number, student_id, school_id, amount,
                    receipt_generated_at, amount_in_words, payment_date, payment_method,
                    paid_by, paid_by_name, generated_by, sms_sent, email_sent, created_at
                )
                VALUES (
                    :receiptId, :paymentId, :receiptNumber, :studentId, :schoolId, 5000,
                    :generatedAt, 'Five Thousand Naira Only', :paymentDate, 'PAYSTACK',
                    :paidBy, 'Ada Parent', :generatedBy, true, false, :createdAt
                )
                """)
                .bind("receiptId", UUID.randomUUID())
                .bind("paymentId", paymentId)
                .bind("receiptNumber", receiptNumber)
                .bind("studentId", studentId)
                .bind("schoolId", SCHOOL_ID)
                .bind("generatedAt", Instant.parse("2026-06-05T10:02:00Z"))
                .bind("paymentDate", Instant.parse("2026-06-05T10:00:00Z"))
                .bind("paidBy", paidBy)
                .bind("generatedBy", paidBy)
                .bind("createdAt", Instant.parse("2026-06-05T10:02:00Z"))
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

    private record PaymentFixture(UUID paymentId, UUID studentId, UUID studentFeeId) {
    }
}
