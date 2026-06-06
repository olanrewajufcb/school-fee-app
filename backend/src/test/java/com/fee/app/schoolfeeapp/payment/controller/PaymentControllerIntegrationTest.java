package com.fee.app.schoolfeeapp.payment.controller;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.dto.GatewayResponse;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGateway;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGatewaySelector;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.mockito.Mockito;
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
class PaymentControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    private static final String PAYSTACK_SECRET = "sk_test_integration";

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
        registry.add("payment.paystack.secret-key", () -> PAYSTACK_SECRET);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private PaymentGatewaySelector gatewaySelector;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private PaymentGateway paymentGateway;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID PARENT_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    @BeforeEach
    void setUp() {
        reset(gatewaySelector, keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        paymentGateway = Mockito.mock(PaymentGateway.class);
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.isAvailable(SCHOOL_ID)).thenReturn(Mono.just(true));
        when(paymentGateway.initiatePayment(any(), any(), any(), any()))
                .thenReturn(Mono.just(GatewayResponse.builder()
                        .gatewayTransactionRef("paystack-controller")
                        .status("PROCESSING")
                        .message("Paystack checkout initialized")
                        .expiresInSeconds(3600)
                        .build()));
        cleanDatabase();
    }

    @Nested
    @DisplayName("POST /api/v1/payments")
    class InitiatePaymentEndpointIntegrationTests {

        @Test
        @DisplayName("Should initiate payment for parent")
        void shouldInitiatePaymentForParent() {
            PaymentFixture fixture = seedPaymentFixture(true);

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .post()
                    .uri("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000)))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.status").isEqualTo("PROCESSING")
                    .jsonPath("$.data.paymentMethod").isEqualTo("PAYSTACK")
                    .jsonPath("$.data.amount").isEqualTo(5000)
                    .jsonPath("$.data.checkoutRequestId").isEqualTo("paystack-controller");

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE school_id = :schoolId
                      AND status = 'PROCESSING'
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payment_allocations
                    WHERE student_fee_id = :studentFeeId
                    """, Map.of("studentFeeId", fixture.studentFeeId())))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject overpayment")
        void shouldRejectOverpayment() {
            PaymentFixture fixture = seedPaymentFixture(true);

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .post()
                    .uri("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(12000)))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("OVERPAYMENT");
        }

        @Test
        @DisplayName("Should reject parent without fee access")
        void shouldRejectParentWithoutFeeAccess() {
            PaymentFixture fixture = seedPaymentFixture(false);

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .post()
                    .uri("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000)))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("ACCESS_DENIED");
        }

        @Test
        @DisplayName("Should reject invalid request")
        void shouldRejectInvalidRequest() {
            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .post()
                    .uri("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new InitiatePaymentRequest(
                            List.of(),
                            "PAYSTACK",
                            "08012345678",
                            BigDecimal.valueOf(5000),
                            null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR");
        }

        @Test
        @DisplayName("Should reject school admin role")
        void shouldRejectSchoolAdminRole() {
            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .post()
                    .uri("/api/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(validRequest(UUID.randomUUID(), BigDecimal.valueOf(5000)))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("Remaining payment endpoints")
    class RemainingPaymentEndpointIntegrationTests {

        @Test
        @DisplayName("Should get payment status for parent")
        void shouldGetPaymentStatusForParent() {
            PaymentFixture fixture = seedPaymentFixture(true);
            UUID paymentId = seedCompletedPayment(fixture);

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .get()
                    .uri("/api/v1/payments/{paymentId}", paymentId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.paymentId").isEqualTo(paymentId.toString())
                    .jsonPath("$.data.status").isEqualTo("COMPLETED")
                    .jsonPath("$.data.receipt.receiptNumber").isEqualTo("RCP-2026-ABC123")
                    .jsonPath("$.data.receipt.breakdown[0].studentName").isEqualTo("Ada Lovelace");
        }

        @Test
        @DisplayName("Should reject parent status for another payer")
        void shouldRejectParentStatusForAnotherPayer() {
            PaymentFixture fixture = seedPaymentFixture(true);
            UUID otherUserId = UUID.randomUUID();
            seedAuthUser(otherUserId, "PARENT", "other-parent@gis.edu");
            UUID paymentId = seedCompletedPayment(fixture, otherUserId, "RCP-2026-OTHER");

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .get()
                    .uri("/api/v1/payments/{paymentId}", paymentId)
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("ACCESS_DENIED");
        }

        @Test
        @DisplayName("Should get parent payment history")
        void shouldGetParentPaymentHistory() {
            PaymentFixture fixture = seedPaymentFixture(true);
            seedCompletedPayment(fixture);

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/payments/history")
                            .queryParam("studentId", fixture.studentId())
                            .queryParam("page", 0)
                            .queryParam("size", 10)
                            .build())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.content[0].status").isEqualTo("COMPLETED")
                    .jsonPath("$.data.content[0].receiptNumber").isEqualTo("RCP-2026-ABC123")
                    .jsonPath("$.data.totalElements").isEqualTo(1)
                    .jsonPath("$.data.totalPages").isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject history for student without guardian fee access")
        void shouldRejectHistoryWithoutGuardianFeeAccess() {
            PaymentFixture fixture = seedPaymentFixture(false);

            authenticatedClient(SCHOOL_ID, "PARENT", "PARENT")
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/payments/history")
                            .queryParam("studentId", fixture.studentId())
                            .build())
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("ACCESS_DENIED");
        }

        @Test
        @DisplayName("Should record offline payment")
        void shouldRecordOfflinePayment() {
            PaymentFixture fixture = seedPaymentFixture(true);

            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/payments/offline")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(offlineRequest(fixture.studentFeeId(), BigDecimal.valueOf(3000), true))
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.status").isEqualTo("COMPLETED")
                    .jsonPath("$.data.receiptNumber").exists();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE payment_mode = 'OFFLINE'
                      AND status = 'COMPLETED'
                    """, Map.of()))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should reject offline overpayment")
        void shouldRejectOfflineOverpayment() {
            PaymentFixture fixture = seedPaymentFixture(true);

            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .post()
                    .uri("/api/v1/payments/offline")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(offlineRequest(fixture.studentFeeId(), BigDecimal.valueOf(12000), false))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("OVERPAYMENT");

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE payment_mode = 'OFFLINE'
                    """, Map.of()))
                    .isZero();
        }

        @Test
        @DisplayName("Should process Paystack webhook without authentication")
        void shouldProcessPaystackWebhookWithoutAuthentication() {
            PaymentFixture fixture = seedPaymentFixture(true);
            String checkoutRequestId = "checkout-webhook";
            String rawPayload = paystackWebhookPayload(
                    checkoutRequestId, "paystack-webhook-txn", BigDecimal.valueOf(5000));
            seedProcessingPayment(fixture, checkoutRequestId, BigDecimal.valueOf(5000));
            when(paymentGateway.handleCallback(rawPayload))
                    .thenReturn(Mono.just(paystackSuccessCallback(
                            checkoutRequestId, "paystack-webhook-txn", BigDecimal.valueOf(5000))));

            webTestClient.post()
                    .uri("/api/v1/webhooks/paystack/callback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-paystack-signature", paystackSignature(rawPayload))
                    .bodyValue(rawPayload)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.message").isEqualTo("Webhook processed successfully");

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE status = 'COMPLETED'
                      AND gateway_transaction_ref = 'checkout-webhook'
                    """, Map.of()))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.receipts
                    """, Map.of()))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isEqualTo(1);
        }
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

    private InitiatePaymentRequest validRequest(UUID studentFeeId, BigDecimal amount) {
        return new InitiatePaymentRequest(
                List.of(studentFeeId),
                "PAYSTACK",
                "08012345678",
                amount,
                null);
    }

    private OfflinePaymentRequest offlineRequest(UUID studentFeeId, BigDecimal amount, boolean generateReceipt) {
        return new OfflinePaymentRequest(
                studentFeeId,
                amount,
                "CASH",
                Instant.now(),
                "Bursar",
                "Paid at office",
                generateReceipt);
    }

    private String paystackWebhookPayload(String reference, String receiptNumber, BigDecimal amount) {
        long kobo = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        return """
                {"event":"charge.success","data":{"id":"%s","reference":"%s","status":"success","amount":%d,"gateway_response":"Approved","customer":{"phone":"08012345678"},"metadata":{"payment_id":"%s"}}}
                """.formatted(receiptNumber, reference, kobo, reference).trim();
    }

    private GatewayCallbackData paystackSuccessCallback(
            String reference, String receiptNumber, BigDecimal amount) {
        return GatewayCallbackData.builder()
                .gatewayTransactionRef(reference)
                .gatewayReceiptNumber(receiptNumber)
                .amount(amount)
                .phoneNumber("08012345678")
                .isSuccess(true)
                .resultDescription("Approved")
                .build();
    }

    private String paystackSignature(String rawPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(PAYSTACK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PaymentFixture seedPaymentFixture(boolean linkGuardian) {
        seedSchool();
        seedAuthUser();
        UUID sessionId = seedSession();
        UUID termId = seedTerm(sessionId);
        UUID classId = seedClass(sessionId);
        UUID studentId = seedStudent(classId);
        if (linkGuardian) {
            seedGuardianLink(studentId);
        }
        UUID structureId = seedFeeStructure(sessionId, termId);
        UUID studentFeeId = seedStudentFee(structureId, studentId);
        seedFeeAssignedLedger(studentFeeId, studentId, BigDecimal.valueOf(10000));
        return new PaymentFixture(studentId, studentFeeId);
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

    private void seedAuthUser() {
        seedAuthUser(PARENT_USER_ID, "PARENT", "parent@gis.edu");
    }

    private void seedAuthUser(UUID userId, String userType, String email) {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id, keycloak_id, school_id, email, phone, first_name, last_name, user_type, is_active
                )
                VALUES (
                    :userId, :keycloakId, :schoolId, :email, :phone,
                    'Parent', 'User', :userType, true
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

    private UUID seedTerm(UUID sessionId) {
        UUID termId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO school.terms (
                    id, session_id, name, term_number, start_date, end_date, is_current, status
                )
                VALUES (
                    :termId, :sessionId, 'First Term', 1,
                    :startDate, :endDate, true, 'ACTIVE'
                )
                """)
                .bind("termId", termId)
                .bind("sessionId", sessionId)
                .bind("startDate", LocalDate.now().minusMonths(1))
                .bind("endDate", LocalDate.now().plusMonths(2))
                .fetch()
                .rowsUpdated()
                .block();
        return termId;
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

    private void seedGuardianLink(UUID studentId) {
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
                .bind("userId", PARENT_USER_ID)
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

    private UUID seedFeeStructure(UUID sessionId, UUID termId) {
        UUID structureId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO fee.fee_structures (
                    id, school_id, name, academic_session_id, term_id,
                    total_amount, due_date, status, created_by
                )
                VALUES (
                    :structureId, :schoolId, 'Primary 1 Tuition',
                    :sessionId, :termId, 10000, :dueDate, 'ACTIVE', :createdBy
                )
                """)
                .bind("structureId", structureId)
                .bind("schoolId", SCHOOL_ID)
                .bind("sessionId", sessionId)
                .bind("termId", termId)
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

    private void seedFeeAssignedLedger(UUID studentFeeId, UUID studentId, BigDecimal amount) {
        databaseClient.sql("""
                INSERT INTO fee.ledger_entries (
                    id, student_fee_id, school_id, student_id, entry_type,
                    amount, balance_after, source_entity_type, source_entity_id,
                    description, transaction_date, idempotency_key, recorded_by
                )
                VALUES (
                    :entryId, :studentFeeId, :schoolId, :studentId, 'FEE_ASSIGNED',
                    :amount, :amount, 'fee_structure', :sourceEntityId,
                    'Fee assigned', :transactionDate, :idempotencyKey, :recordedBy
                )
                """)
                .bind("entryId", UUID.randomUUID())
                .bind("studentFeeId", studentFeeId)
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", studentId)
                .bind("amount", amount)
                .bind("sourceEntityId", UUID.randomUUID())
                .bind("transactionDate", Instant.now())
                .bind("idempotencyKey", UUID.randomUUID())
                .bind("recordedBy", PARENT_USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private UUID seedCompletedPayment(PaymentFixture fixture) {
        return seedCompletedPayment(fixture, PARENT_USER_ID, "RCP-2026-ABC123");
    }

    private UUID seedCompletedPayment(PaymentFixture fixture, UUID paidBy, String receiptNumber) {
        UUID paymentId = seedPayment(
                fixture,
                paidBy,
                "COMPLETED",
                "paystack-ref-123",
                "SUCCESS",
                BigDecimal.valueOf(5000));
        seedPaymentAllocation(paymentId, fixture.studentFeeId(), BigDecimal.valueOf(5000));
        seedPaymentLedger(paymentId, fixture.studentFeeId(), fixture.studentId(), paidBy, BigDecimal.valueOf(5000));
        seedReceipt(paymentId, fixture.studentId(), paidBy, receiptNumber, BigDecimal.valueOf(5000));
        return paymentId;
    }

    private UUID seedProcessingPayment(PaymentFixture fixture, String checkoutRequestId, BigDecimal amount) {
        UUID paymentId = seedPayment(
                fixture,
                PARENT_USER_ID,
                "PROCESSING",
                checkoutRequestId,
                "PROCESSING",
                amount);
        seedPaymentAllocation(paymentId, fixture.studentFeeId(), amount);
        return paymentId;
    }

    private UUID seedPayment(
            PaymentFixture fixture,
            UUID paidBy,
            String status,
            String gatewayRef,
            String gatewayStatus,
            BigDecimal amount) {
        UUID paymentId = UUID.randomUUID();
        databaseClient.sql("""
                INSERT INTO payment.payments (
                    id, student_fee_id, student_id, school_id, amount,
                    payment_method, payment_gateway, gateway_transaction_ref, gateway_status,
                    payment_mode, status, paid_by, payer_phone, idempotency_key,
                    created_at, updated_at
                )
                VALUES (
                    :paymentId, :studentFeeId, :studentId, :schoolId, :amount,
                    'PAYSTACK', 'PAYSTACK', :gatewayRef, :gatewayStatus,
                    'ONLINE', :status, :paidBy, '08012345678', :idempotencyKey,
                    :createdAt, :updatedAt
                )
                """)
                .bind("paymentId", paymentId)
                .bind("studentFeeId", fixture.studentFeeId())
                .bind("studentId", fixture.studentId())
                .bind("schoolId", SCHOOL_ID)
                .bind("amount", amount)
                .bind("gatewayRef", gatewayRef)
                .bind("gatewayStatus", gatewayStatus)
                .bind("status", status)
                .bind("paidBy", paidBy)
                .bind("idempotencyKey", UUID.randomUUID().toString())
                .bind("createdAt", Instant.now())
                .bind("updatedAt", Instant.now())
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

    private void seedPaymentLedger(
            UUID paymentId, UUID studentFeeId, UUID studentId, UUID paidBy, BigDecimal amount) {
        databaseClient.sql("""
                INSERT INTO fee.ledger_entries (
                    id, student_fee_id, school_id, student_id, entry_type,
                    amount, balance_after, source_entity_type, source_entity_id,
                    description, transaction_date, idempotency_key, recorded_by, system_action
                )
                VALUES (
                    :entryId, :studentFeeId, :schoolId, :studentId, 'PAYMENT',
                    :amount, :balanceAfter, 'payment', :paymentId,
                    'Paystack payment', :transactionDate, :idempotencyKey, :recordedBy, 'PAYSTACK_CALLBACK'
                )
                """)
                .bind("entryId", UUID.randomUUID())
                .bind("studentFeeId", studentFeeId)
                .bind("schoolId", SCHOOL_ID)
                .bind("studentId", studentId)
                .bind("amount", amount.negate())
                .bind("balanceAfter", BigDecimal.valueOf(10000).subtract(amount))
                .bind("paymentId", paymentId)
                .bind("transactionDate", Instant.now())
                .bind("idempotencyKey", UUID.randomUUID())
                .bind("recordedBy", paidBy)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedReceipt(
            UUID paymentId, UUID studentId, UUID paidBy, String receiptNumber, BigDecimal amount) {
        databaseClient.sql("""
                INSERT INTO payment.receipts (
                    id, payment_id, receipt_number, student_id, school_id, amount,
                    payment_date, payment_method, paid_by, generated_by, sms_sent, email_sent, created_at
                )
                VALUES (
                    :receiptId, :paymentId, :receiptNumber, :studentId, :schoolId, :amount,
                    :paymentDate, 'PAYSTACK', :paidBy, :paidBy, false, false, :createdAt
                )
                """)
                .bind("receiptId", UUID.randomUUID())
                .bind("paymentId", paymentId)
                .bind("receiptNumber", receiptNumber)
                .bind("studentId", studentId)
                .bind("schoolId", SCHOOL_ID)
                .bind("amount", amount)
                .bind("paymentDate", Instant.now())
                .bind("paidBy", paidBy)
                .bind("createdAt", Instant.now())
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

    private long countRows(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return ((Number) spec.fetch().one().block().get("count")).longValue();
    }

    private record PaymentFixture(UUID studentId, UUID studentFeeId) {
    }
}
