package com.fee.app.schoolfeeapp.receipt.service.impl;

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
class ReceiptServiceImplIntegrationTest {

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
    private ReceiptServiceImpl receiptService;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID PARENT_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID OTHER_PARENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ACCOUNTANT_USER_ID = UUID.fromString("d3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final String RECEIPT_NUMBER = "RCP-2026-ABC123";

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(jwtUtils, keycloakAdminService, reactiveJwtDecoder);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    @DisplayName("Should return receipt details for owning parent")
    void shouldReturnReceiptDetailsForOwningParent() {
        PaymentFixture fixture = seedReceiptFixture(PARENT_USER_ID, RECEIPT_NUMBER);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

        StepVerifier.create(receiptService.getReceiptDetails(RECEIPT_NUMBER))
                .assertNext(response -> {
                    assertThat(response.receiptNumber()).isEqualTo(RECEIPT_NUMBER);
                    assertThat(response.paymentId()).isEqualTo(fixture.paymentId());
                    assertThat(response.schoolName()).isEqualTo("Grace International School");
                    assertThat(response.schoolAddress()).isEqualTo("12 School Road, Lagos, Lagos");
                    assertThat(response.paidBy()).isEqualTo("Ada Parent");
                    assertThat(response.amount()).isEqualByComparingTo("5000");
                    assertThat(response.amountInWords()).isEqualTo("Five Thousand Naira Only");
                    assertThat(response.paymentMethod()).isEqualTo("PAYSTACK");
                    assertThat(response.breakdown()).hasSize(1);
                    assertThat(response.breakdown().getFirst().studentName()).isEqualTo("Ada Lovelace");
                    assertThat(response.breakdown().getFirst().amount()).isEqualByComparingTo("5000");
                    assertThat(response.smsSent()).isTrue();
                    assertThat(response.emailSent()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject parent reading another payer receipt")
    void shouldRejectParentReadingAnotherPayerReceipt() {
        seedReceiptFixture(OTHER_PARENT_ID, RECEIPT_NUMBER);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

        StepVerifier.create(receiptService.getReceiptDetails(RECEIPT_NUMBER))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should download real PDF bytes for school staff")
    void shouldDownloadRealPdfBytesForSchoolStaff() {
        seedReceiptFixture(PARENT_USER_ID, RECEIPT_NUMBER);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));

        StepVerifier.create(receiptService.downloadReceiptPdf(RECEIPT_NUMBER))
                .assertNext(buffer -> {
                    byte[] bytes = readBytes(buffer);
                    assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
                    assertThat(bytes.length).isGreaterThan(100);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return receipt not found")
    void shouldReturnReceiptNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

        StepVerifier.create(receiptService.getReceiptDetails("RCP-MISSING"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("RECEIPT_NOT_FOUND");
                })
                .verify();
    }

    private byte[] readBytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }

    private PaymentFixture seedReceiptFixture(UUID paidBy, String receiptNumber) {
        seedSchool();
        seedAuthUser(PARENT_USER_ID, "PARENT", "parent@gis.edu");
        if (!PARENT_USER_ID.equals(paidBy)) {
            seedAuthUser(paidBy, "PARENT", "other-parent@gis.edu");
        }
        seedAuthUser(ACCOUNTANT_USER_ID, "ACCOUNTANT", "accountant@gis.edu");
        UUID sessionId = seedSession();
        UUID classId = seedClass(sessionId);
        UUID studentId = seedStudent(classId);
        UUID structureId = seedFeeStructure(sessionId);
        UUID studentFeeId = seedStudentFee(structureId, studentId);
        UUID paymentId = seedPayment(studentFeeId, studentId, paidBy);
        seedPaymentAllocation(paymentId, studentFeeId, BigDecimal.valueOf(5000));
        seedReceipt(paymentId, studentId, paidBy, receiptNumber);
        return new PaymentFixture(paymentId, studentId, studentFeeId);
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
                .bind("keycloakId", UUID.randomUUID())
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

    private SchoolFeeUser parentUser() {
        return SchoolFeeUser.builder()
                .userId(PARENT_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("PARENT")
                .roles(Set.of("PARENT"))
                .build();
    }

    private SchoolFeeUser accountantUser() {
        return SchoolFeeUser.builder()
                .userId(ACCOUNTANT_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("ACCOUNTANT")
                .roles(Set.of("ACCOUNTANT"))
                .build();
    }

    private record PaymentFixture(UUID paymentId, UUID studentId, UUID studentFeeId) {
    }
}
