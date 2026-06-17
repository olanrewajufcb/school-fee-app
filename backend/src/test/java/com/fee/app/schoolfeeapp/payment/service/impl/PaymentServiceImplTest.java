package com.fee.app.schoolfeeapp.payment.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.domain.LedgerEntry;
import com.fee.app.schoolfeeapp.fee.domain.StudentFee;
import com.fee.app.schoolfeeapp.fee.repository.LedgerEntryRepository;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeStructureRepository;
import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.payment.domain.PaymentAllocation;
import com.fee.app.schoolfeeapp.payment.domain.Receipt;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.dto.GatewayResponse;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGateway;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGatewaySelector;
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.repository.PaymentAllocationRepository;
import com.fee.app.schoolfeeapp.payment.repository.PaymentRepository;
import com.fee.app.schoolfeeapp.payment.repository.ReceiptRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.SchoolStudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentAllocationRepository allocationRepository;
    @Mock
    private ReceiptRepository receiptRepository;
    @Mock
    private StudentFeeRepository studentFeeRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private SchoolStudentGuardianLinkRepository guardianLinkRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private TransactionalOperator transactionalOperator;
    @Mock
    private PaymentGatewaySelector gatewaySelector;
    @Mock
    private PaymentGateway paymentGateway;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FeeStructureRepository feeStructureRepository;

    private PaymentServiceImpl paymentService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID PARENT_USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID STUDENT_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID STUDENT_FEE_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                allocationRepository,
                receiptRepository,
                studentFeeRepository,
                ledgerEntryRepository,
                studentRepository,
                guardianLinkRepository,
                jwtUtils,
                transactionalOperator,
                gatewaySelector,
                userRepository,
                feeStructureRepository);

        org.mockito.Mockito.lenient().when(userRepository.findByKeycloakIdAndDeletedAtIsNull(PARENT_USER_ID))
                .thenReturn(Mono.just(User.builder().id(PARENT_USER_ID).keycloakId(PARENT_USER_ID).build()));
    }

    @Test
    @DisplayName("Should initiate payment and reserve selected fee amount")
    void shouldInitiatePaymentAndReserveSelectedFeeAmount() {
        stubParentAndGateway();
        stubPayableFee(BigDecimal.valueOf(10000), BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(allocationRepository.save(any(PaymentAllocation.class)))
                .thenAnswer(invocation -> {
                    PaymentAllocation allocation = invocation.getArgument(0);
                    allocation.setId(UUID.randomUUID());
                    return Mono.just(allocation);
                });
        when(paymentGateway.initiatePayment(
                any(UUID.class), eq("08012345678"), eq(BigDecimal.valueOf(5000)), eq("School fee payment")))
                .thenReturn(Mono.just(GatewayResponse.builder()
                        .gatewayTransactionRef("paystack-ref-123")
                        .status("PROCESSING")
                        .message("Paystack checkout initialized")
                        .expiresInSeconds(3600)
                        .build()));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(5000))))
                .assertNext(response -> {
                    assertThat(response.paymentId()).isNotNull();
                    assertThat(response.status()).isEqualTo("PROCESSING");
                    assertThat(response.paymentMethod()).isEqualTo("PAYSTACK");
                    assertThat(response.amount()).isEqualByComparingTo("5000");
                    assertThat(response.checkoutRequestId()).isEqualTo("paystack-ref-123");
                })
                .verifyComplete();

        ArgumentCaptor<PaymentAllocation> allocationCaptor =
                ArgumentCaptor.forClass(PaymentAllocation.class);
        verify(allocationRepository).save(allocationCaptor.capture());
        assertThat(allocationCaptor.getValue().getSchoolId()).isEqualTo(SCHOOL_ID);
        assertThat(allocationCaptor.getValue().getStudentFeeId()).isEqualTo(STUDENT_FEE_ID);
        assertThat(allocationCaptor.getValue().getAmount()).isEqualByComparingTo("5000");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.times(2)).save(paymentCaptor.capture());
        Payment finalPayment = paymentCaptor.getAllValues().get(1);
        assertThat(finalPayment.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(finalPayment.getStatus()).isEqualTo("PROCESSING");
        assertThat(finalPayment.getGatewayTransactionRef()).isEqualTo("paystack-ref-123");
        assertThat(finalPayment.getIdempotencyKey()).isNotBlank();
    }

    @Test
    @DisplayName("Should reject non-parent before gateway selection")
    void shouldRejectNonParentBeforeGatewaySelection() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(PARENT_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build()));

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(5000))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(gatewaySelector, never()).select(any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should reject overpayment against live balance")
    void shouldRejectOverpaymentAgainstLiveBalance() {
        stubParentAndGateway();
        stubPayableFee(BigDecimal.valueOf(10000), BigDecimal.ZERO);
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(12000))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("OVERPAYMENT");
                })
                .verify();

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(paymentGateway, never()).initiatePayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject payment when fee balance is already reserved")
    void shouldRejectPaymentWhenFeeBalanceIsAlreadyReserved() {
        stubParentAndGateway();
        stubPayableFee(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(5000))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("PAYMENT_IN_PROGRESS");
                })
                .verify();

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should mark payment failed when gateway initiation fails")
    void shouldMarkPaymentFailedWhenGatewayInitiationFails() {
        stubParentAndGateway();
        stubPayableFee(BigDecimal.valueOf(10000), BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(allocationRepository.save(any(PaymentAllocation.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(paymentGateway.initiatePayment(any(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("gateway down")));
        when(paymentRepository.findById(any(UUID.class)))
                .thenAnswer(invocation -> Mono.just(Payment.builder()
                        .id(invocation.getArgument(0))
                        .schoolId(SCHOOL_ID)
                        .studentId(STUDENT_ID)
                        .studentFeeId(STUDENT_FEE_ID)
                        .amount(BigDecimal.valueOf(5000))
                        .paymentMethod("PAYSTACK")
                        .paymentMode("ONLINE")
                        .status("PENDING")
                        .paidBy(PARENT_USER_ID)
                        .build()));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(5000))))
                .expectErrorMessage("gateway down")
                .verify();

        verify(paymentRepository, org.mockito.Mockito.times(2)).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("Should reject invalid request before auth lookup")
    void shouldRejectInvalidRequestBeforeAuthLookup() {
        InitiatePaymentRequest request = new InitiatePaymentRequest(
                List.of(),
                "PAYSTACK",
                "08012345678",
                BigDecimal.valueOf(5000),
                null);

        StepVerifier.create(paymentService.initiatePayment(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeIds");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should return payment status with receipt and breakdown")
    void shouldReturnPaymentStatusWithReceiptAndBreakdown() {
        UUID paymentId = UUID.randomUUID();
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID))
                .thenReturn(Mono.just(completedPayment(paymentId)));
        when(allocationRepository.findByPaymentId(paymentId))
                .thenReturn(Flux.just(paymentAllocation(paymentId, BigDecimal.valueOf(5000))));
        when(studentFeeRepository.findById(STUDENT_FEE_ID)).thenReturn(Mono.just(studentFee()));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(student()));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.just(receipt(paymentId)));

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.paymentId()).isEqualTo(paymentId);
                    assertThat(response.status()).isEqualTo("COMPLETED");
                    assertThat(response.receipt()).isNotNull();
                    assertThat(response.receipt().receiptNumber()).isEqualTo("RCP-2026-ABC123");
                    assertThat(response.receipt().breakdown()).hasSize(1);
                    assertThat(response.receipt().breakdown().getFirst().studentName())
                            .isEqualTo("Ada Lovelace");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject parent reading another payer payment status")
    void shouldRejectParentReadingAnotherPayerPaymentStatus() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        payment.setPaidBy(UUID.randomUUID());
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(allocationRepository, never()).findByPaymentId(any());
    }

    @Test
    @DisplayName("Should return parent payment history with real totals")
    void shouldReturnParentPaymentHistoryWithRealTotals() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByPaidByAndSchoolIdOrderByCreatedAtDesc(
                PARENT_USER_ID, SCHOOL_ID, 10, 0))
                .thenReturn(Flux.just(payment));
        when(paymentRepository.countByPaidByAndSchoolId(PARENT_USER_ID, SCHOOL_ID))
                .thenReturn(Mono.just(12L));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.just(receipt(paymentId)));

        StepVerifier.create(paymentService.getPaymentHistory(null, PageRequest.of(0, 10)))
                .assertNext(response -> {
                    assertThat(response.content()).hasSize(1);
                    assertThat(response.totalElements()).isEqualTo(12);
                    assertThat(response.totalPages()).isEqualTo(2);
                    assertThat(response.content().getFirst().receiptNumber()).isEqualTo("RCP-2026-ABC123");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject parent history for student without fee access")
    void shouldRejectParentHistoryForStudentWithoutFeeAccess() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(guardianLinkRepository.findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(
                PARENT_USER_ID, STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentHistory(STUDENT_ID, PageRequest.of(0, 10)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(paymentRepository, never()).findByStudentIdAndSchoolIdOrderByCreatedAtDesc(
                any(), any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("Should process successful Paystack callback once")
    void shouldProcessSuccessfulPaystackCallbackOnce() {
        UUID paymentId = UUID.randomUUID();
        String rawPayload = rawPaystackPayload();
        Payment payment = processingPayment(paymentId, "paystack-ref-123", BigDecimal.valueOf(5000));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(paystackSuccessCallback(
                        "paystack-ref-123", "txn-123", BigDecimal.valueOf(5000))));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123"))
                .thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(allocationRepository.findByPaymentId(paymentId))
                .thenReturn(Flux.just(paymentAllocation(paymentId, BigDecimal.valueOf(5000))));
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee()));
        when(ledgerEntryRepository.findTopByStudentFeeIdOrderByCreatedAtDesc(STUDENT_FEE_ID))
                .thenReturn(Mono.just(feeAssignedLedger(BigDecimal.valueOf(10000))));
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());
        when(receiptRepository.save(any(Receipt.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .verifyComplete();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(paymentCaptor.getValue().getGatewayTransactionRef()).isEqualTo("paystack-ref-123");
        assertThat(paymentCaptor.getValue().getNarration()).isEqualTo("Paystack transaction: txn-123");
        assertThat(paymentCaptor.getValue().getIdempotencyKey()).isNotBlank();

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getAmount()).isEqualByComparingTo("-5000");
        assertThat(ledgerCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("5000");
        assertThat(ledgerCaptor.getValue().getSystemAction()).isEqualTo("PAYSTACK_CALLBACK");
        verify(receiptRepository).save(any(Receipt.class));
    }

    @Test
    @DisplayName("Should ignore duplicate Paystack callback")
    void shouldIgnoreDuplicatePaystackCallback() {
        String rawPayload = rawPaystackPayload();
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(paystackSuccessCallback(
                        "paystack-ref-123", "txn-123", BigDecimal.valueOf(5000))));
        when(paymentRepository.findByIdempotencyKey(anyString()))
                .thenReturn(Mono.just(completedPayment(UUID.randomUUID())));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .verifyComplete();

        verify(paymentRepository, never()).findByGatewayTransactionRefForUpdate(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should reject successful callback with mismatched amount")
    void shouldRejectSuccessfulCallbackWithMismatchedAmount() {
        UUID paymentId = UUID.randomUUID();
        String rawPayload = rawPaystackPayload();
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(paystackSuccessCallback(
                        "paystack-ref-123", "txn-123", BigDecimal.valueOf(4000))));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123"))
                .thenReturn(Mono.just(processingPayment(paymentId, "paystack-ref-123", BigDecimal.valueOf(5000))));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("CALLBACK_AMOUNT_MISMATCH");
                })
                .verify();

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should mark payment failed from failed Paystack callback")
    void shouldMarkPaymentFailedFromFailedPaystackCallback() {
        UUID paymentId = UUID.randomUUID();
        String rawPayload = rawPaystackPayload();
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(GatewayCallbackData.builder()
                        .gatewayTransactionRef("paystack-ref-123")
                        .gatewayReceiptNumber("txn-123")
                        .isSuccess(false)
                        .resultDescription("Card declined")
                        .rawPayload(rawPayload)
                        .build()));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123"))
                .thenReturn(Mono.just(processingPayment(paymentId, "paystack-ref-123", BigDecimal.valueOf(5000))));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .verifyComplete();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(paymentCaptor.getValue().getGatewayStatus()).isEqualTo("FAILED");
        assertThat(paymentCaptor.getValue().getNarration()).isEqualTo("Card declined");
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should record offline payment with locked balance and receipt")
    void shouldRecordOfflinePaymentWithLockedBalanceAndReceipt() {
        Instant paymentDate = Instant.parse("2026-06-04T10:15:30Z");
        OfflinePaymentRequest request = offlineRequest(BigDecimal.valueOf(3000), paymentDate, true);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(STUDENT_FEE_ID))
                .thenReturn(Flux.just(feeAssignedLedger(BigDecimal.valueOf(10000))));
        when(allocationRepository.sumActiveAllocatedAmount(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(BigDecimal.ZERO));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(allocationRepository.save(any(PaymentAllocation.class)))
                .thenAnswer(invocation -> {
                    PaymentAllocation allocation = invocation.getArgument(0);
                    allocation.setId(UUID.randomUUID());
                    return Mono.just(allocation);
                });
        when(ledgerEntryRepository.findTopByStudentFeeIdOrderByCreatedAtDesc(STUDENT_FEE_ID))
                .thenReturn(Mono.just(feeAssignedLedger(BigDecimal.valueOf(10000))));
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(receiptRepository.save(any(Receipt.class))).thenReturn(Mono.just(receipt(UUID.randomUUID())));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.recordOfflinePayment(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("COMPLETED");
                    assertThat(response.receiptNumber()).isEqualTo("RCP-2026-ABC123");
                    assertThat(response.approvedBy()).isEqualTo("Bursar");
                })
                .verifyComplete();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentMode()).isEqualTo("OFFLINE");
        assertThat(paymentCaptor.getValue().getCreatedAt()).isEqualTo(paymentDate);

        ArgumentCaptor<LedgerEntry> ledgerCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(ledgerCaptor.capture());
        assertThat(ledgerCaptor.getValue().getAmount()).isEqualByComparingTo("-3000");
        assertThat(ledgerCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("7000");
        assertThat(ledgerCaptor.getValue().getTransactionDate()).isEqualTo(paymentDate);
    }

    @Test
    @DisplayName("Should reject offline overpayment before writes")
    void shouldRejectOfflineOverpaymentBeforeWrites() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(STUDENT_FEE_ID))
                .thenReturn(Flux.just(feeAssignedLedger(BigDecimal.valueOf(10000))));
        when(allocationRepository.sumActiveAllocatedAmount(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(BigDecimal.ZERO));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.recordOfflinePayment(
                        offlineRequest(BigDecimal.valueOf(12000), Instant.now(), false)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("OVERPAYMENT");
                })
                .verify();

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Should reject offline payment by parent")
    void shouldRejectOfflinePaymentByParent() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

        StepVerifier.create(paymentService.recordOfflinePayment(
                        offlineRequest(BigDecimal.valueOf(3000), Instant.now(), false)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();

        verify(studentFeeRepository, never()).findByIdAndSchoolIdForUpdate(any(), any());
    }

    private void stubParentAndGateway() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.isAvailable(SCHOOL_ID)).thenReturn(Mono.just(true));
    }

    private void stubPayableFee(BigDecimal balanceAfter, BigDecimal reservedAmount) {
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee()));
        when(guardianLinkRepository.findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(
                PARENT_USER_ID, STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder()
                        .id(UUID.randomUUID())
                        .studentId(STUDENT_ID)
                        .schoolId(SCHOOL_ID)
                        .canViewFees(true)
                        .build()));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(STUDENT_FEE_ID))
                .thenReturn(Flux.just(LedgerEntry.builder()
                        .id(UUID.randomUUID())
                        .studentFeeId(STUDENT_FEE_ID)
                        .studentId(STUDENT_ID)
                        .schoolId(SCHOOL_ID)
                        .entryType("FEE_ASSIGNED")
                        .amount(BigDecimal.valueOf(10000))
                        .balanceAfter(balanceAfter)
                        .createdAt(Instant.now())
                        .build()));
        when(allocationRepository.sumActiveAllocatedAmount(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(reservedAmount));
    }

    private InitiatePaymentRequest validRequest(BigDecimal amount) {
        return new InitiatePaymentRequest(
                List.of(STUDENT_FEE_ID),
                "paystack",
                "08012345678",
                amount,
                null);
    }

    private OfflinePaymentRequest offlineRequest(BigDecimal amount, Instant paymentDate, boolean generateReceipt) {
        return new OfflinePaymentRequest(
                STUDENT_FEE_ID,
                amount,
                " cash ",
                paymentDate,
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
                .userId(PARENT_USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("ACCOUNTANT")
                .roles(Set.of("ACCOUNTANT"))
                .build();
    }

    private StudentFee studentFee() {
        return StudentFee.builder()
                .id(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .totalAmount(BigDecimal.valueOf(10000))
                .discountAmount(BigDecimal.ZERO)
                .dueDate(LocalDate.now().plusDays(30))
                .build();
    }

    private Student student() {
        return Student.builder()
                .id(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .firstName("Ada")
                .lastName("Lovelace")
                .admissionNumber("STU260010")
                .build();
    }

    private Payment processingPayment(UUID paymentId, String checkoutRequestId, BigDecimal amount) {
        return Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(amount)
                .paymentMethod("PAYSTACK")
                .paymentGateway("PAYSTACK")
                .paymentMode("ONLINE")
                .gatewayTransactionRef(checkoutRequestId)
                .gatewayStatus("PROCESSING")
                .status("PROCESSING")
                .paidBy(PARENT_USER_ID)
                .payerPhone("08012345678")
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private Payment completedPayment(UUID paymentId) {
        return Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .paymentMethod("PAYSTACK")
                .paymentGateway("PAYSTACK")
                .paymentMode("ONLINE")
                .gatewayTransactionRef("paystack-ref-123")
                .gatewayStatus("SUCCESS")
                .status("COMPLETED")
                .paidBy(PARENT_USER_ID)
                .payerPhone("08012345678")
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private PaymentAllocation paymentAllocation(UUID paymentId, BigDecimal amount) {
        return PaymentAllocation.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .paymentId(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .amount(amount)
                .createdAt(Instant.now())
                .build();
    }

    private LedgerEntry feeAssignedLedger(BigDecimal balanceAfter) {
        return LedgerEntry.builder()
                .id(UUID.randomUUID())
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .entryType("FEE_ASSIGNED")
                .amount(BigDecimal.valueOf(10000))
                .balanceAfter(balanceAfter)
                .sourceEntityType("fee_structure")
                .sourceEntityId(UUID.randomUUID())
                .transactionDate(Instant.now())
                .idempotencyKey(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }

    private Receipt receipt(UUID paymentId) {
        return Receipt.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .receiptNumber("RCP-2026-ABC123")
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .paymentDate(Instant.now())
                .paymentMethod("PAYSTACK")
                .paidBy(PARENT_USER_ID)
                .generatedBy(PARENT_USER_ID)
                .createdAt(Instant.now())
                .build();
    }
}
