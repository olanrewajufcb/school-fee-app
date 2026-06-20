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
import java.util.ArrayList;
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

    // ========================================================================
    // INITIATE PAYMENT VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject initiatePayment when request is null")
    void shouldRejectInitiatePaymentWhenRequestIsNull() {
        StepVerifier.create(paymentService.initiatePayment(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject initiatePayment when studentFeeIds is null or empty")
    void shouldRejectInitiatePaymentWhenStudentFeeIdsEmpty() {
        InitiatePaymentRequest req1 = new InitiatePaymentRequest(null, "PAYSTACK", "08012345678", BigDecimal.valueOf(5000), null);
        InitiatePaymentRequest req2 = new InitiatePaymentRequest(List.of(), "PAYSTACK", "08012345678", BigDecimal.valueOf(5000), null);

        StepVerifier.create(paymentService.initiatePayment(req1))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeIds");
                })
                .verify();

        StepVerifier.create(paymentService.initiatePayment(req2))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeIds");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject initiatePayment when fee ID in list is null")
    void shouldRejectInitiatePaymentWhenFeeIdInListIsNull() {
        List<UUID> list = new ArrayList<>();
        list.add(null);
        InitiatePaymentRequest req = new InitiatePaymentRequest(list, "PAYSTACK", "08012345678", BigDecimal.valueOf(5000), null);
        StepVerifier.create(paymentService.initiatePayment(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeIds");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject initiatePayment when payment method is blank")
    void shouldRejectInitiatePaymentWhenPaymentMethodBlank() {
        InitiatePaymentRequest req = new InitiatePaymentRequest(List.of(STUDENT_FEE_ID), " ", "08012345678", BigDecimal.valueOf(5000), null);
        StepVerifier.create(paymentService.initiatePayment(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("paymentMethod");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject initiatePayment when phone number is blank")
    void shouldRejectInitiatePaymentWhenPhoneNumberBlank() {
        InitiatePaymentRequest req = new InitiatePaymentRequest(List.of(STUDENT_FEE_ID), "PAYSTACK", "", BigDecimal.valueOf(5000), null);
        StepVerifier.create(paymentService.initiatePayment(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("phoneNumber");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject initiatePayment when amount is null or negative")
    void shouldRejectInitiatePaymentWhenAmountInvalid() {
        InitiatePaymentRequest req1 = new InitiatePaymentRequest(List.of(STUDENT_FEE_ID), "PAYSTACK", "08012345678", null, null);
        InitiatePaymentRequest req2 = new InitiatePaymentRequest(List.of(STUDENT_FEE_ID), "PAYSTACK", "08012345678", BigDecimal.ZERO, null);
        InitiatePaymentRequest req3 = new InitiatePaymentRequest(List.of(STUDENT_FEE_ID), "PAYSTACK", "08012345678", BigDecimal.valueOf(-10), null);

        StepVerifier.create(paymentService.initiatePayment(req1))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"))
                .verify();

        StepVerifier.create(paymentService.initiatePayment(req2))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"))
                .verify();

        StepVerifier.create(paymentService.initiatePayment(req3))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"))
                .verify();
    }

    @Test
    @DisplayName("Should reject initiatePayment when amount is less than 1000 and not full balance")
    void shouldRejectInitiatePaymentWhenLessThanMinAmount() {
        stubParentAndGateway();
        stubPayableFee(BigDecimal.valueOf(10000), BigDecimal.ZERO);
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        InitiatePaymentRequest req = new InitiatePaymentRequest(List.of(STUDENT_FEE_ID), "PAYSTACK", "08012345678", BigDecimal.valueOf(500), null);
        StepVerifier.create(paymentService.initiatePayment(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_AMOUNT");
                })
                .verify();
    }

    // ========================================================================
    // RECORD OFFLINE PAYMENT VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject recordOfflinePayment when request is null")
    void shouldRejectRecordOfflinePaymentWhenRequestIsNull() {
        StepVerifier.create(paymentService.recordOfflinePayment(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject recordOfflinePayment when studentFeeId is null")
    void shouldRejectRecordOfflinePaymentWhenStudentFeeIdIsNull() {
        OfflinePaymentRequest req = new OfflinePaymentRequest(null, BigDecimal.valueOf(5000), "CASH", Instant.now(), "Bursar", "Notes", false);
        StepVerifier.create(paymentService.recordOfflinePayment(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeId");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject recordOfflinePayment when amount is invalid")
    void shouldRejectRecordOfflinePaymentWhenAmountInvalid() {
        OfflinePaymentRequest req1 = new OfflinePaymentRequest(STUDENT_FEE_ID, null, "CASH", Instant.now(), "Bursar", "Notes", false);
        OfflinePaymentRequest req2 = new OfflinePaymentRequest(STUDENT_FEE_ID, BigDecimal.valueOf(-5), "CASH", Instant.now(), "Bursar", "Notes", false);

        StepVerifier.create(paymentService.recordOfflinePayment(req1))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"))
                .verify();

        StepVerifier.create(paymentService.recordOfflinePayment(req2))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"))
                .verify();
    }

    @Test
    @DisplayName("Should reject recordOfflinePayment when payment method is blank")
    void shouldRejectRecordOfflinePaymentWhenPaymentMethodBlank() {
        OfflinePaymentRequest req = new OfflinePaymentRequest(STUDENT_FEE_ID, BigDecimal.valueOf(5000), " ", Instant.now(), "Bursar", "Notes", false);
        StepVerifier.create(paymentService.recordOfflinePayment(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("paymentMethod");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject recordOfflinePayment when payment date is null")
    void shouldRejectRecordOfflinePaymentWhenPaymentDateIsNull() {
        OfflinePaymentRequest req = new OfflinePaymentRequest(STUDENT_FEE_ID, BigDecimal.valueOf(5000), "CASH", null, "Bursar", "Notes", false);
        StepVerifier.create(paymentService.recordOfflinePayment(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("paymentDate");
                })
                .verify();
    }

    // ========================================================================
    // WEBHOOK CALLBACK VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject success callback when transaction ref is null")
    void shouldRejectSuccessCallbackWhenTransactionRefIsNull() {
        String rawPayload = "raw";
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(GatewayCallbackData.builder()
                        .isSuccess(true)
                        .gatewayTransactionRef(null)
                        .amount(BigDecimal.valueOf(5000))
                        .build()));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CALLBACK");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("reference");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject success callback when amount is invalid")
    void shouldRejectSuccessCallbackWhenAmountInvalid() {
        String rawPayload = "raw";
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(GatewayCallbackData.builder()
                        .isSuccess(true)
                        .gatewayTransactionRef("ref-123")
                        .amount(BigDecimal.ZERO)
                        .build()));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_CALLBACK");
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("amount");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject failure callback when transaction ref is null")
    void shouldRejectFailureCallbackWhenTransactionRefIsNull() {
        String rawPayload = "raw";
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(GatewayCallbackData.builder()
                        .isSuccess(false)
                        .gatewayTransactionRef(null)
                        .build()));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .verifyComplete();
    }

    // ========================================================================
    // VERIFY AND UPDATE PAYMENT (EXPIRATION AND GATEWAY VERIFICATION)
    // ========================================================================

    @Test
    @DisplayName("Should expire payment without gateway ref if older than 15 minutes")
    void shouldExpirePaymentWithoutGatewayRefIfOld() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .status("PENDING")
                .paidBy(PARENT_USER_ID)
                .gatewayTransactionRef(null)
                .createdAt(Instant.now().minus(java.time.Duration.ofMinutes(20)))
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.empty());
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("FAILED");
                })
                .verifyComplete();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(paymentCaptor.getValue().getNarration()).isEqualTo("Payment expired/abandoned");
    }

    @Test
    @DisplayName("Should not expire payment without gateway ref if newer than 15 minutes")
    void shouldNotExpirePaymentWithoutGatewayRefIfNew() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .status("PENDING")
                .paidBy(PARENT_USER_ID)
                .gatewayTransactionRef(null)
                .createdAt(Instant.now().minus(java.time.Duration.ofMinutes(5)))
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.empty());
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("PENDING");
                })
                .verifyComplete();

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should expire payment on failed verification if older than 15 minutes")
    void shouldExpirePaymentOnFailedVerificationIfOld() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .paymentMethod("PAYSTACK")
                .status("PENDING")
                .paidBy(PARENT_USER_ID)
                .gatewayTransactionRef("ref-123")
                .createdAt(Instant.now().minus(java.time.Duration.ofMinutes(20)))
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        
        com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus failedVerification =
                com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus.builder()
                        .isSuccess(false)
                        .resultDescription("Declined")
                        .build();
        when(paymentGateway.verifyPayment("ref-123")).thenReturn(Mono.just(failedVerification));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.empty());
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("FAILED");
                })
                .verifyComplete();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(paymentCaptor.getValue().getNarration()).isEqualTo("Verification failed/abandoned: Declined");
    }

    @Test
    @DisplayName("Should mark payment failed on network error verify payment if older than 15 minutes")
    void shouldExpirePaymentOnVerifyErrorIfOld() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .paymentMethod("PAYSTACK")
                .status("PENDING")
                .paidBy(PARENT_USER_ID)
                .gatewayTransactionRef("ref-123")
                .createdAt(Instant.now().minus(java.time.Duration.ofMinutes(20)))
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.verifyPayment("ref-123")).thenReturn(Mono.error(new RuntimeException("timeout")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.empty());
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("FAILED");
                })
                .verifyComplete();

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(paymentCaptor.getValue().getNarration()).isEqualTo("Verification failed (network/server error)");
    }

    // ========================================================================
    // ADDITIONAL SCENARIOS
    // ========================================================================

    @Test
    @DisplayName("Should reject initiatePayment when fee is not found")
    void shouldRejectInitiatePaymentWhenFeeNotFound() {
        stubParentAndGateway();
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(5000))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("FEE_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject successful callback when payment status is FAILED")
    void shouldRejectSuccessfulCallbackWhenPaymentFailed() {
        UUID paymentId = UUID.randomUUID();
        String rawPayload = rawPaystackPayload();
        Payment payment = processingPayment(paymentId, "paystack-ref-123", BigDecimal.valueOf(5000));
        payment.setStatus("FAILED");

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(paystackSuccessCallback(
                        "paystack-ref-123", "txn-123", BigDecimal.valueOf(5000))));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123"))
                .thenReturn(Mono.just(payment));
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_STATE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should return student history for school staff")
    void shouldReturnStudentHistoryForSchoolStaff() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(paymentRepository.findByStudentIdAndSchoolIdOrderByCreatedAtDesc(
                STUDENT_ID, SCHOOL_ID, 10, 0))
                .thenReturn(Flux.just(payment));
        when(paymentRepository.countByStudentIdAndSchoolId(STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(1L));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.just(receipt(paymentId)));

        StepVerifier.create(paymentService.getPaymentHistory(STUDENT_ID, PageRequest.of(0, 10)))
                .assertNext(response -> {
                    assertThat(response.content()).hasSize(1);
                    assertThat(response.totalElements()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return school-wide history for school staff")
    void shouldReturnSchoolWideHistoryForSchoolStaff() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(paymentRepository.findBySchoolIdOrderByCreatedAtDesc(
                SCHOOL_ID, 10, 0))
                .thenReturn(Flux.just(payment));
        when(paymentRepository.countBySchoolId(SCHOOL_ID))
                .thenReturn(Mono.just(5L));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.just(receipt(paymentId)));

        StepVerifier.create(paymentService.getPaymentHistory(null, PageRequest.of(0, 10)))
                .assertNext(response -> {
                    assertThat(response.content()).hasSize(1);
                    assertThat(response.totalElements()).isEqualTo(5);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should map payment history response with default description when narration is null and fee not found")
    void shouldMapHistoryResponseWithDefaultDescWhenFeeNotFound() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        payment.setNarration(null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(paymentRepository.findBySchoolIdOrderByCreatedAtDesc(SCHOOL_ID, 10, 0))
                .thenReturn(Flux.just(payment));
        when(paymentRepository.countBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(1L));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());
        when(studentFeeRepository.findById(STUDENT_FEE_ID)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentHistory(null, PageRequest.of(0, 10)))
                .assertNext(response -> {
                    assertThat(response.content().getFirst().description()).isEqualTo("Fee payment");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should map payment history response with default description when fee structure not found")
    void shouldMapHistoryResponseWithDefaultDescWhenStructureNotFound() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        payment.setNarration(null);

        StudentFee fee = studentFee();
        fee.setFeeStructureId(UUID.randomUUID());

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(paymentRepository.findBySchoolIdOrderByCreatedAtDesc(SCHOOL_ID, 10, 0))
                .thenReturn(Flux.just(payment));
        when(paymentRepository.countBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(1L));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());
        when(studentFeeRepository.findById(STUDENT_FEE_ID)).thenReturn(Mono.just(fee));
        when(feeStructureRepository.findById(any(UUID.class))).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentHistory(null, PageRequest.of(0, 10)))
                .assertNext(response -> {
                    assertThat(response.content().getFirst().description()).isEqualTo("Fee payment");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle null updateMono when expiring stuck payments")
    void shouldHandleNullUpdateMonoWhenExpiringStuckPayments() {
        when(paymentRepository.expireStuckPayments(any())).thenReturn(null);

        StepVerifier.create(paymentService.initiatePayment(null))
                .expectError(SchoolFeeException.class)
                .verify();
    }

    @Test
    @DisplayName("Should reject get payment status when paymentId is null")
    void shouldRejectGetPaymentStatusWhenPaymentIdIsNull() {
        StepVerifier.create(paymentService.getPaymentStatus(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject payment when gateway is not available")
    void shouldRejectPaymentWhenGatewayIsNotAvailable() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.isAvailable(SCHOOL_ID)).thenReturn(Mono.just(false));

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(5000))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should not expire payment on verify error if it is new")
    void shouldNotExpirePaymentOnVerifyErrorIfItIsNew() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .paymentMethod("PAYSTACK")
                .status("PENDING")
                .paidBy(PARENT_USER_ID)
                .gatewayTransactionRef("ref-123")
                .createdAt(Instant.now().minus(java.time.Duration.ofMinutes(5)))
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.verifyPayment("ref-123")).thenReturn(Mono.error(new RuntimeException("timeout")));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.empty());
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("PENDING");
                })
                .verifyComplete();

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should not update payment status on failed verification if it is new")
    void shouldNotUpdatePaymentStatusOnFailedVerificationIfItIsNew() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = Payment.builder()
                .id(paymentId)
                .studentFeeId(STUDENT_FEE_ID)
                .studentId(STUDENT_ID)
                .schoolId(SCHOOL_ID)
                .amount(BigDecimal.valueOf(5000))
                .paymentMethod("PAYSTACK")
                .status("PENDING")
                .paidBy(PARENT_USER_ID)
                .gatewayTransactionRef("ref-123")
                .createdAt(Instant.now().minus(java.time.Duration.ofMinutes(5)))
                .build();

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        
        com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus failedVerification =
                com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus.builder()
                        .isSuccess(false)
                        .resultDescription("Declined")
                        .build();
        when(paymentGateway.verifyPayment("ref-123")).thenReturn(Mono.just(failedVerification));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.empty());
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("PENDING");
                })
                .verifyComplete();

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should map payment history response with default description when studentFeeId is null")
    void shouldMapHistoryResponseWithDefaultDescWhenStudentFeeIdIsNull() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        payment.setNarration(null);
        payment.setStudentFeeId(null);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(paymentRepository.findBySchoolIdOrderByCreatedAtDesc(SCHOOL_ID, 10, 0))
                .thenReturn(Flux.just(payment));
        when(paymentRepository.countBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(1L));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentHistory(null, PageRequest.of(0, 10)))
                .assertNext(response -> {
                    assertThat(response.content().getFirst().description()).isEqualTo("Fee payment");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should ignore duplicate failed Paystack callback")
    void shouldIgnoreDuplicateFailedPaystackCallback() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(false)
                .resultDescription("Declined")
                .build();

        Payment payment = completedPayment(UUID.randomUUID());

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.just(payment));

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject failed Paystack callback when payment in invalid state")
    void shouldRejectFailedCallbackWhenInvalidState() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(false)
                .resultDescription("Declined")
                .build();

        Payment payment = completedPayment(UUID.randomUUID());
        payment.setStatus("PENDING");
        payment.setPaymentMode("OFFLINE");

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByGatewayTransactionRefForUpdate("ref-123")).thenReturn(Mono.just(payment));

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_STATE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject successful Paystack callback when payment is not open online payment")
    void shouldRejectSuccessfulCallbackWhenInvalidState() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(true)
                .build();

        Payment payment = completedPayment(UUID.randomUUID());
        payment.setStatus("PENDING");
        payment.setPaymentMode("OFFLINE");

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByGatewayTransactionRefForUpdate("ref-123")).thenReturn(Mono.just(payment));

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_PAYMENT_STATE");
                })
                .verify();
    }

    @Test
    @DisplayName("Should ignore duplicate successful Paystack callback")
    void shouldIgnoreDuplicateSuccessfulCallback() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(true)
                .build();

        Payment payment = completedPayment(UUID.randomUUID());

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.just(payment));

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw exception when callback has no pending payment and not exists in idempotency key")
    void shouldThrowExceptionWhenCallbackHasNoPendingPayment() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(true)
                .build();

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByGatewayTransactionRefForUpdate("ref-123")).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("PAYMENT_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should throw exception when failure callback has no pending payment and not exists in idempotency key")
    void shouldThrowExceptionWhenFailureCallbackHasNoPendingPayment() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(false)
                .build();

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findByGatewayTransactionRefForUpdate("ref-123")).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("PAYMENT_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should calculate total pages correctly when limit is invalid or elements are empty")
    void shouldCalculateTotalPagesCorrectlyWhenLimitInvalidOrElementsEmpty() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(paymentRepository.findBySchoolIdOrderByCreatedAtDesc(eq(SCHOOL_ID), anyInt(), anyLong()))
                .thenReturn(Flux.empty());
        when(paymentRepository.countBySchoolId(SCHOOL_ID)).thenReturn(Mono.just(0L));

        StepVerifier.create(paymentService.getPaymentHistory(null, PageRequest.of(0, 1)))
                .assertNext(response -> {
                    assertThat(response.totalPages()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @DisplayName("Should reject record offline payment when user is not authorized")
    void shouldRejectRecordOfflinePaymentWhenNotAuthorized() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));

        OfflinePaymentRequest request = offlineRequest(BigDecimal.valueOf(5000), Instant.now(), true);

        StepVerifier.create(paymentService.recordOfflinePayment(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("ACCESS_DENIED");
                })
                .verify();
    }

    @Test
    @DisplayName("Should verify and update payment status on successful gateway verification")
    void shouldVerifyAndUpdatePaymentOnGatewaySuccess() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = processingPayment(paymentId, "paystack-ref-123", BigDecimal.valueOf(5000));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);

        com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus verifySuccess =
                com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus.builder()
                        .isSuccess(true)
                        .gatewayReceiptNumber("txn-123")
                        .amount(BigDecimal.valueOf(5000))
                        .phoneNumber("08012345678")
                        .resultDescription("Approved")
                        .build();
        when(paymentGateway.verifyPayment("paystack-ref-123")).thenReturn(Mono.just(verifySuccess));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123")).thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.just(paymentAllocation(paymentId, BigDecimal.valueOf(5000))));
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID)).thenReturn(Mono.just(studentFee()));
        when(studentFeeRepository.findById(STUDENT_FEE_ID)).thenReturn(Mono.just(studentFee()));
        when(studentRepository.findById(STUDENT_ID)).thenReturn(Mono.just(student()));
        when(ledgerEntryRepository.findTopByStudentFeeIdOrderByCreatedAtDesc(STUDENT_FEE_ID)).thenReturn(Mono.just(feeAssignedLedger(BigDecimal.valueOf(10000))));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));
        when(paymentRepository.findById(paymentId)).thenReturn(Mono.just(completedPayment(paymentId)));

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("COMPLETED");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should allow payment less than min amount if paying full balance")
    void shouldAllowPaymentLessThanMinAmountIfPayingFullBalance() {
        stubParentAndGateway();
        stubPayableFee(BigDecimal.valueOf(500), BigDecimal.ZERO);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.save(any(PaymentAllocation.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(paymentGateway.initiatePayment(any(), any(), any(), any()))
                .thenReturn(Mono.just(GatewayResponse.builder()
                        .gatewayTransactionRef("ref-123")
                        .status("PROCESSING")
                        .build()));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(500))))
                .assertNext(response -> {
                    assertThat(response.amount()).isEqualByComparingTo("500");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should allow school staff to get payment status of any parent")
    void shouldAllowSchoolStaffToGetPaymentStatus() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = completedPayment(paymentId);
        payment.setPaidBy(UUID.randomUUID()); // paid by someone else

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountantUser()));
        when(paymentRepository.findByIdAndSchoolId(paymentId, SCHOOL_ID)).thenReturn(Mono.just(payment));
        when(allocationRepository.findByPaymentId(paymentId)).thenReturn(Flux.empty());
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());

        StepVerifier.create(paymentService.getPaymentStatus(paymentId))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("COMPLETED");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should ignore webhook if payment is already completed")
    void shouldIgnoreWebhookIfPaymentIsAlreadyCompleted() {
        UUID paymentId = UUID.randomUUID();
        String rawPayload = rawPaystackPayload();
        Payment payment = completedPayment(paymentId);

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(paystackSuccessCallback("paystack-ref-123", "txn-123", BigDecimal.valueOf(5000))));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123")).thenReturn(Mono.just(payment));
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate balance when last entry balance after is null")
    void shouldCalculateBalanceWhenLastEntryBalanceAfterIsNull() {
        stubParentAndGateway();
        
        StudentFee fee = studentFee();
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(fee));
        when(guardianLinkRepository.findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(PARENT_USER_ID, STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder().studentId(STUDENT_ID).schoolId(SCHOOL_ID).canViewFees(true).build()));
        
        LedgerEntry entry1 = LedgerEntry.builder().amount(BigDecimal.valueOf(10000)).balanceAfter(null).build();
        LedgerEntry entry2 = LedgerEntry.builder().amount(BigDecimal.valueOf(-2000)).balanceAfter(null).build();
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(STUDENT_FEE_ID))
                .thenReturn(Flux.just(entry1, entry2));
        
        when(allocationRepository.sumActiveAllocatedAmount(STUDENT_FEE_ID, SCHOOL_ID))
                .thenReturn(Mono.just(BigDecimal.ZERO));
        
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        // Available balance should be 10000 - 2000 = 8000. So initiating 9000 should throw OVERPAYMENT.
        StepVerifier.create(paymentService.initiatePayment(validRequest(BigDecimal.valueOf(9000))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("OVERPAYMENT");
                })
                .verify();
    }

    @Test
    @DisplayName("Should break allocation loop when remaining amount becomes zero")
    void shouldBreakAllocationLoopWhenRemainingIsZero() {
        stubParentAndGateway();
        
        UUID studentFeeId1 = STUDENT_FEE_ID;
        UUID studentFeeId2 = UUID.randomUUID();
        
        StudentFee fee1 = studentFee();
        StudentFee fee2 = StudentFee.builder().id(studentFeeId2).studentId(STUDENT_ID).schoolId(SCHOOL_ID).totalAmount(BigDecimal.valueOf(5000)).build();
        
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(studentFeeId1, SCHOOL_ID)).thenReturn(Mono.just(fee1));
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(studentFeeId2, SCHOOL_ID)).thenReturn(Mono.just(fee2));
        
        when(guardianLinkRepository.findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(PARENT_USER_ID, STUDENT_ID, SCHOOL_ID))
                .thenReturn(Mono.just(StudentGuardianLink.builder().studentId(STUDENT_ID).schoolId(SCHOOL_ID).canViewFees(true).build()));
        
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId1))
                .thenReturn(Flux.just(feeAssignedLedger(BigDecimal.valueOf(10000))));
        when(ledgerEntryRepository.findByStudentFeeIdOrderByCreatedAtAsc(studentFeeId2))
                .thenReturn(Flux.just(feeAssignedLedger(BigDecimal.valueOf(5000))));
        
        when(allocationRepository.sumActiveAllocatedAmount(studentFeeId1, SCHOOL_ID)).thenReturn(Mono.just(BigDecimal.ZERO));
        when(allocationRepository.sumActiveAllocatedAmount(studentFeeId2, SCHOOL_ID)).thenReturn(Mono.just(BigDecimal.ZERO));
        
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.save(any(PaymentAllocation.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        
        when(paymentGateway.initiatePayment(any(), any(), any(), any()))
                .thenReturn(Mono.just(GatewayResponse.builder().gatewayTransactionRef("ref-123").status("PROCESSING").build()));
        
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        InitiatePaymentRequest req = new InitiatePaymentRequest(List.of(studentFeeId1, studentFeeId2), "PAYSTACK", "08012345678", BigDecimal.valueOf(5000), null);

        StepVerifier.create(paymentService.initiatePayment(req))
                .assertNext(response -> {
                    assertThat(response.amount()).isEqualByComparingTo("5000");
                })
                .verifyComplete();

        // Should only save 1 allocation because the first fee allocation consumed all 5000 remaining amount
        verify(allocationRepository, times(1)).save(any(PaymentAllocation.class));
    }

    @Test
    @DisplayName("Should reject allocation when it exceeds fee balance")
    void shouldRejectAllocationWhenItExceedsFeeBalance() {
        UUID paymentId = UUID.randomUUID();
        String rawPayload = rawPaystackPayload();
        Payment payment = processingPayment(paymentId, "paystack-ref-123", BigDecimal.valueOf(5000));

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(paystackSuccessCallback("paystack-ref-123", "txn-123", BigDecimal.valueOf(5000))));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123")).thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.findByPaymentId(paymentId))
                .thenReturn(Flux.just(paymentAllocation(paymentId, BigDecimal.valueOf(5000))));
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID)).thenReturn(Mono.just(studentFee()));
        
        // Mock lastEntry to have balanceAfter = 4000 (less than 5000 allocated amount)
        LedgerEntry lastEntry = LedgerEntry.builder().balanceAfter(BigDecimal.valueOf(4000)).build();
        when(ledgerEntryRepository.findTopByStudentFeeIdOrderByCreatedAtDesc(STUDENT_FEE_ID)).thenReturn(Mono.just(lastEntry));
        
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.empty());
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("OVERPAYMENT");
                })
                .verify();
    }

    @Test
    @DisplayName("Should avoid duplicate receipt generation if receipt already exists")
    void shouldAvoidDuplicateReceiptGeneration() {
        UUID paymentId = UUID.randomUUID();
        String rawPayload = rawPaystackPayload();
        Payment payment = processingPayment(paymentId, "paystack-ref-123", BigDecimal.valueOf(5000));

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback(rawPayload))
                .thenReturn(Mono.just(paystackSuccessCallback("paystack-ref-123", "txn-123", BigDecimal.valueOf(5000))));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.empty());
        when(paymentRepository.findByGatewayTransactionRefForUpdate("paystack-ref-123")).thenReturn(Mono.just(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(allocationRepository.findByPaymentId(paymentId))
                .thenReturn(Flux.just(paymentAllocation(paymentId, BigDecimal.valueOf(5000))));
        when(studentFeeRepository.findByIdAndSchoolIdForUpdate(STUDENT_FEE_ID, SCHOOL_ID)).thenReturn(Mono.just(studentFee()));
        when(ledgerEntryRepository.findTopByStudentFeeIdOrderByCreatedAtDesc(STUDENT_FEE_ID)).thenReturn(Mono.just(feeAssignedLedger(BigDecimal.valueOf(10000))));
        when(ledgerEntryRepository.save(any(LedgerEntry.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        
        // Return existing receipt
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Mono.just(receipt(paymentId)));
        
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(i -> i.getArgument(0));

        StepVerifier.create(paymentService.handlePaystackWebhook(rawPayload))
                .verifyComplete();

        // receiptRepository.save should never be called since receipt already exists
        verify(receiptRepository, never()).save(any(Receipt.class));
    }

    @Test
    @DisplayName("Should ignore webhook if payment not found but idempotency key exists")
    void shouldIgnoreWebhookIfPaymentNotFoundButIdempotencyKeyExists() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(true)
                .build();

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.just(completedPayment(UUID.randomUUID())));

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should ignore failed webhook if payment not found but idempotency key exists")
    void shouldIgnoreFailedWebhookIfPaymentNotFoundButIdempotencyKeyExists() {
        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                .gatewayTransactionRef("ref-123")
                .amount(BigDecimal.valueOf(5000))
                .isSuccess(false)
                .build();

        when(gatewaySelector.select("PAYSTACK")).thenReturn(paymentGateway);
        when(paymentGateway.handleCallback("payload")).thenReturn(Mono.just(callbackData));
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Mono.just(completedPayment(UUID.randomUUID())));

        StepVerifier.create(paymentService.handlePaystackWebhook("payload"))
                .verifyComplete();
    }
}

