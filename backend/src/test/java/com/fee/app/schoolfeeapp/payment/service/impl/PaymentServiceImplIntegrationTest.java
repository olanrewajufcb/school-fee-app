package com.fee.app.schoolfeeapp.payment.service.impl;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.response.InitiatePaymentResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.OfflinePaymentResponse;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.dto.GatewayResponse;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGateway;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGatewaySelector;
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
import org.mockito.Mockito;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentServiceImplIntegrationTest {

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
    private PaymentServiceImpl paymentService;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private PaymentGatewaySelector gatewaySelector;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private PaymentGateway paymentGateway;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID PARENT_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID ACCOUNTANT_USER_ID = UUID.fromString("d3d4e5f6-a7b8-9012-cdef-123456789012");

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(jwtUtils, gatewaySelector, keycloakAdminService, reactiveJwtDecoder);
        paymentGateway = Mockito.mock(PaymentGateway.class);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.isAvailable(SCHOOL_ID)).thenReturn(Mono.just(true));
        when(paymentGateway.initiatePayment(any(), any(), any(), any()))
                .thenReturn(Mono.just(GatewayResponse.builder()
                        .gatewayTransactionRef("paystack-" + UUID.randomUUID())
                        .status("PROCESSING")
                        .message("Paystack checkout initialized")
                        .expiresInSeconds(3600)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Nested
    @DisplayName("Initiate Payment - Service Integration Tests")
    class InitiatePaymentIntegrationTests {

        @Test
        @DisplayName("Should create processing payment and reserve allocation")
        void shouldCreateProcessingPaymentAndReserveAllocation() {
            PaymentFixture fixture = seedPaymentFixture(true);

            StepVerifier.create(paymentService.initiatePayment(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000))))
                    .assertNext(response -> {
                        assertThat(response.paymentId()).isNotNull();
                        assertThat(response.status()).isEqualTo("PROCESSING");
                        assertThat(response.paymentMethod()).isEqualTo("PAYSTACK");
                        assertThat(response.amount()).isEqualByComparingTo("5000");
                        assertThat(response.checkoutRequestId()).startsWith("paystack-");
                    })
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE school_id = :schoolId
                      AND status = 'PROCESSING'
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .isEqualTo(1);
            assertThat(sumAmount("""
                    SELECT COALESCE(SUM(amount), 0) AS amount
                    FROM payment.payment_allocations
                    WHERE student_fee_id = :studentFeeId
                    """, Map.of("studentFeeId", fixture.studentFeeId())))
                    .isEqualByComparingTo("5000");
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isZero();
        }

        @Test
        @DisplayName("Should reject parent without fee access")
        void shouldRejectParentWithoutFeeAccess() {
            PaymentFixture fixture = seedPaymentFixture(false);

            StepVerifier.create(paymentService.initiatePayment(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000))))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                    })
                    .verify();

            assertThat(countRows("SELECT COUNT(*) AS count FROM payment.payments", Map.of()))
                    .isZero();
        }

        @Test
        @DisplayName("Should reject overpayment against outstanding balance")
        void shouldRejectOverpaymentAgainstOutstandingBalance() {
            PaymentFixture fixture = seedPaymentFixture(true);

            StepVerifier.create(paymentService.initiatePayment(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(12000))))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("OVERPAYMENT");
                    })
                    .verify();

            assertThat(countRows("SELECT COUNT(*) AS count FROM payment.payments", Map.of()))
                    .isZero();
        }

        @Test
        @DisplayName("Should serialize concurrent initiations for same fee")
        void shouldSerializeConcurrentInitiationsForSameFee() throws Exception {
            PaymentFixture fixture = seedPaymentFixture(true);

            CompletableFuture<Object> first = initiateAsync(fixture.studentFeeId(), BigDecimal.valueOf(10000));
            CompletableFuture<Object> second = initiateAsync(fixture.studentFeeId(), BigDecimal.valueOf(10000));

            Object firstResult = first.get(10, TimeUnit.SECONDS);
            Object secondResult = second.get(10, TimeUnit.SECONDS);

            List<Object> results = List.of(firstResult, secondResult);
            assertThat(results.stream()
                    .filter(result -> result instanceof InitiatePaymentResponse)
                    .count())
                    .isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> result instanceof SchoolFeeException exception
                            && "PAYMENT_IN_PROGRESS".equals(exception.getErrorCode()))
                    .count())
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payment_allocations
                    WHERE student_fee_id = :studentFeeId
                    """, Map.of("studentFeeId", fixture.studentFeeId())))
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Remaining Payment Service Methods - Integration Tests")
    class RemainingPaymentMethodIntegrationTests {

        @Test
        @DisplayName("Should complete Paystack callback idempotently")
        void shouldCompletePaystackCallbackIdempotently() {
            PaymentFixture fixture = seedPaymentFixture(true);
            InitiatePaymentResponse initiated = paymentService
                    .initiatePayment(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000)))
                    .block(Duration.ofSeconds(5));
            String rawPayload = rawPaystackPayload();
            when(paymentGateway.handleCallback(rawPayload))
                    .thenReturn(Mono.just(paystackSuccessCallback(
                            initiated.checkoutRequestId(), "paystack-txn-123", BigDecimal.valueOf(5000))));

            paymentService.handlePaystackWebhook(rawPayload)
                    .block(Duration.ofSeconds(5));
            paymentService.handlePaystackWebhook(rawPayload)
                    .block(Duration.ofSeconds(5));

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE status = 'COMPLETED'
                      AND gateway_transaction_ref = :gatewayRef
                    """, Map.of("gatewayRef", initiated.checkoutRequestId())))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.receipts
                    """, Map.of()))
                    .isEqualTo(1);
            assertThat(sumAmount("""
                    SELECT COALESCE(SUM(balance_after), 0) AS amount
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("Should mark payment failed from failed Paystack callback")
        void shouldMarkPaymentFailedFromFailedPaystackCallback() {
            PaymentFixture fixture = seedPaymentFixture(true);
            InitiatePaymentResponse initiated = paymentService
                    .initiatePayment(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000)))
                    .block(Duration.ofSeconds(5));
            String rawPayload = rawPaystackPayload();
            when(paymentGateway.handleCallback(rawPayload))
                    .thenReturn(Mono.just(paystackFailedCallback(
                            initiated.checkoutRequestId(), "Request cancelled")));

            paymentService.handlePaystackWebhook(rawPayload)
                    .block(Duration.ofSeconds(5));

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE status = 'FAILED'
                      AND narration = 'Request cancelled'
                    """, Map.of()))
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isZero();
            assertThat(sumAmount("""
                    SELECT COALESCE(SUM(a.amount), 0) AS amount
                    FROM payment.payment_allocations a
                    JOIN payment.payments p ON p.id = a.payment_id
                    WHERE a.student_fee_id = :studentFeeId
                      AND p.status IN ('PENDING', 'PROCESSING')
                    """, Map.of("studentFeeId", fixture.studentFeeId())))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Should reject callback amount mismatch")
        void shouldRejectCallbackAmountMismatch() {
            PaymentFixture fixture = seedPaymentFixture(true);
            InitiatePaymentResponse initiated = paymentService
                    .initiatePayment(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000)))
                    .block(Duration.ofSeconds(5));
            String rawPayload = rawPaystackPayload();
            when(paymentGateway.handleCallback(rawPayload))
                    .thenReturn(Mono.just(paystackSuccessCallback(
                            initiated.checkoutRequestId(), "paystack-txn-123", BigDecimal.valueOf(4000))));

            StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CALLBACK_AMOUNT_MISMATCH");
                    })
                    .verify();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE status = 'COMPLETED'
                    """, Map.of()))
                    .isZero();
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isZero();
        }

        @Test
        @DisplayName("Should record offline payment with ledger and receipt")
        void shouldRecordOfflinePaymentWithLedgerAndReceipt() {
            PaymentFixture fixture = seedPaymentFixture(true);
            seedAuthUser(ACCOUNTANT_USER_ID, "ACCOUNTANT");
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));

            StepVerifier.create(paymentService.recordOfflinePayment(offlineRequest(
                            fixture.studentFeeId(), BigDecimal.valueOf(3000), true)))
                    .assertNext(response -> {
                        assertThat(response.status()).isEqualTo("COMPLETED");
                        assertThat(response.paymentId()).isNotNull();
                        assertThat(response.receiptNumber()).startsWith("RCP-");
                    })
                    .verifyComplete();

            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM payment.payments
                    WHERE payment_mode = 'OFFLINE'
                      AND status = 'COMPLETED'
                      AND offline_approved_by = :accountantId
                    """, Map.of("accountantId", ACCOUNTANT_USER_ID)))
                    .isEqualTo(1);
            assertThat(sumAmount("""
                    SELECT COALESCE(SUM(amount), 0) AS amount
                    FROM payment.payment_allocations
                    WHERE student_fee_id = :studentFeeId
                    """, Map.of("studentFeeId", fixture.studentFeeId())))
                    .isEqualByComparingTo("3000");
            assertThat(sumAmount("""
                    SELECT COALESCE(SUM(amount), 0) AS amount
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isEqualByComparingTo("-3000");
            assertThat(sumAmount("""
                    SELECT COALESCE(SUM(balance_after), 0) AS amount
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isEqualByComparingTo("7000");
            assertThat(countRows("SELECT COUNT(*) AS count FROM payment.receipts", Map.of()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should serialize concurrent offline payments for same fee")
        void shouldSerializeConcurrentOfflinePaymentsForSameFee() throws Exception {
            PaymentFixture fixture = seedPaymentFixture(true);
            seedAuthUser(ACCOUNTANT_USER_ID, "ACCOUNTANT");
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));

            CompletableFuture<Object> first = recordOfflineAsync(fixture.studentFeeId(), BigDecimal.valueOf(10000));
            CompletableFuture<Object> second = recordOfflineAsync(fixture.studentFeeId(), BigDecimal.valueOf(10000));

            List<Object> results = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS));

            assertThat(results.stream()
                    .filter(result -> result instanceof OfflinePaymentResponse)
                    .count())
                    .isEqualTo(1);
            assertThat(results.stream()
                    .filter(result -> result instanceof SchoolFeeException exception
                            && ("OVERPAYMENT".equals(exception.getErrorCode())
                            || "FEE_ALREADY_PAID".equals(exception.getErrorCode())))
                    .count())
                    .isEqualTo(1);
            assertThat(countRows("""
                    SELECT COUNT(*) AS count
                    FROM fee.ledger_entries
                    WHERE entry_type = 'PAYMENT'
                    """, Map.of()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("Should return payment status and history for authorized parent")
        void shouldReturnPaymentStatusAndHistoryForAuthorizedParent() {
            PaymentFixture fixture = seedPaymentFixture(true);
            InitiatePaymentResponse initiated = paymentService
                    .initiatePayment(validRequest(fixture.studentFeeId(), BigDecimal.valueOf(5000)))
                    .block(Duration.ofSeconds(5));
            String rawPayload = rawPaystackPayload();
            when(paymentGateway.handleCallback(rawPayload))
                    .thenReturn(Mono.just(paystackSuccessCallback(
                            initiated.checkoutRequestId(), "paystack-txn-123", BigDecimal.valueOf(5000))));
            paymentService.handlePaystackWebhook(rawPayload)
                    .block(Duration.ofSeconds(5));

            StepVerifier.create(paymentService.getPaymentStatus(initiated.paymentId()))
                    .assertNext(response -> {
                        assertThat(response.status()).isEqualTo("COMPLETED");
                        assertThat(response.receipt()).isNotNull();
                        assertThat(response.receipt().breakdown()).hasSize(1);
                    })
                    .verifyComplete();

            StepVerifier.create(paymentService.getPaymentHistory(fixture.studentId(), org.springframework.data.domain.PageRequest.of(0, 10)))
                    .assertNext(response -> {
                        assertThat(response.content()).hasSize(1);
                        assertThat(response.totalElements()).isEqualTo(1);
                        assertThat(response.totalPages()).isEqualTo(1);
                        assertThat(response.content().getFirst().receiptNumber()).startsWith("RCP-");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject parent history without guardian fee access")
        void shouldRejectParentHistoryWithoutGuardianFeeAccess() {
            PaymentFixture fixture = seedPaymentFixture(false);

            StepVerifier.create(paymentService.getPaymentHistory(
                            fixture.studentId(), org.springframework.data.domain.PageRequest.of(0, 10)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                    })
                    .verify();
        }
    }

    private CompletableFuture<Object> initiateAsync(UUID studentFeeId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return paymentService.initiatePayment(validRequest(studentFeeId, amount))
                        .block(Duration.ofSeconds(8));
            } catch (Throwable error) {
                return error;
            }
        });
    }

    private CompletableFuture<Object> recordOfflineAsync(UUID studentFeeId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return paymentService.recordOfflinePayment(offlineRequest(studentFeeId, amount, false))
                        .block(Duration.ofSeconds(8));
            } catch (Throwable error) {
                return error;
            }
        });
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

    private String rawPaystackPayload() {
        return "{\"event\":\"charge.success\"}";
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
                .rawPayload(rawPaystackPayload())
                .build();
    }

    private GatewayCallbackData paystackFailedCallback(String reference, String reason) {
        return GatewayCallbackData.builder()
                .gatewayTransactionRef(reference)
                .gatewayReceiptNumber("paystack-failed-txn")
                .isSuccess(false)
                .resultDescription(reason)
                .rawPayload(rawPaystackPayload())
                .build();
    }

    private PaymentFixture seedPaymentFixture(boolean linkGuardian) {
        seedSchool();
        seedAuthUser(PARENT_USER_ID, "PARENT");
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

    private void seedAuthUser(UUID userId, String userType) {
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
                .bind("email", "parent-" + userId + "@gis.edu")
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

    private BigDecimal sumAmount(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return (BigDecimal) spec.fetch().one().block().get("amount");
    }

    private SchoolFeeUser parentUser() {
        return SchoolFeeUser.builder()
                .userId(PARENT_USER_ID)
                .schoolId(SCHOOL_ID)
                .email("parent@gis.edu")
                .userType("PARENT")
                .roles(Set.of("PARENT"))
                .build();
    }

    private SchoolFeeUser accountantUser() {
        return SchoolFeeUser.builder()
                .userId(ACCOUNTANT_USER_ID)
                .schoolId(SCHOOL_ID)
                .email("accountant@gis.edu")
                .userType("ACCOUNTANT")
                .roles(Set.of("ACCOUNTANT"))
                .build();
    }

    private record PaymentFixture(UUID studentId, UUID studentFeeId) {
    }
}
