package com.fee.app.schoolfeeapp.payment.service.impl;


import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.domain.LedgerEntry;
import com.fee.app.schoolfeeapp.fee.domain.StudentFee;
import com.fee.app.schoolfeeapp.fee.repository.LedgerEntryRepository;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.fee.repository.FeeStructureRepository;
import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.payment.domain.PaymentAllocation;
import com.fee.app.schoolfeeapp.payment.domain.Receipt;
import com.fee.app.schoolfeeapp.payment.dto.request.BankTransferRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.response.*;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGateway;
import com.fee.app.schoolfeeapp.payment.gateway.service.PaymentGatewaySelector;
import com.fee.app.schoolfeeapp.payment.repository.PaymentAllocationRepository;
import com.fee.app.schoolfeeapp.payment.repository.PaymentRepository;
import com.fee.app.schoolfeeapp.payment.repository.ReceiptRepository;
import com.fee.app.schoolfeeapp.payment.service.PaymentService;
import com.fee.app.schoolfeeapp.student.repository.SchoolStudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final ReceiptRepository receiptRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final StudentRepository studentRepository;
    private final SchoolStudentGuardianLinkRepository guardianLinkRepository;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;
    private final PaymentGatewaySelector gatewaySelector;
    private final UserRepository userRepository;
    private final FeeStructureRepository feeStructureRepository;


    // ========================================================================
    // INITIATE PAYMENT
    // ========================================================================
    private Mono<UUID> resolveLocalUserId(UUID keycloakUserId) {
        return userRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakUserId)
                .map(User::getId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "USER_NOT_FOUND",
                        "User not found in the database")));
    }

    @Override
    public Mono<InitiatePaymentResponse> initiatePayment(InitiatePaymentRequest request) {
        return expireStuckPayments()
                .then(Mono.defer(() -> Mono.fromCallable(() ->
                        validateAndNormalizeInitiatePaymentRequest(request))))
                .flatMap(normalizedRequest -> jwtUtils.getCurrentUser()
                        .flatMap(parentUser -> {
                            UUID schoolId = parentUser.getSchoolId();
                            UUID keycloakUserId = parentUser.getUserId();

                            return resolveLocalUserId(keycloakUserId)
                                    .flatMap(localUserId -> {
                                        PaymentGateway gateway = gatewaySelector.select(
                                                normalizedRequest.paymentMethod());

                                        return gateway.isAvailable(schoolId)
                                                .flatMap(available -> {
                                                    if (!Boolean.TRUE.equals(available)) {
                                                        return Mono.error(new SchoolFeeException(
                                                                "PAYMENT_GATEWAY_UNAVAILABLE",
                                                                "Payment gateway is not available"));
                                                    }

                                                    return createPendingPaymentAttempt(
                                                            normalizedRequest, schoolId, localUserId, keycloakUserId)
                                                            .flatMap(savedPayment ->
                                                                    processPaymentWithLock(
                                                                            gateway,
                                                                            savedPayment,
                                                                            normalizedRequest,
                                                                            parentUser.getEmail()));
                                                });
                                    });
                        }));
    }

    /**
     * Process payment with proper state locking and concurrency control.
     * Same pattern as processBankTransferWithLock.
     *
     * State machine: PENDING → PROCESSING → COMPLETED | FAILED
     */
    private Mono<InitiatePaymentResponse> processPaymentWithLock(
            PaymentGateway gateway,
            Payment savedPayment,
            InitiatePaymentRequest request,
            String customerEmail) {

        UUID paymentId = savedPayment.getId();
        String currentStatus = savedPayment.getStatus();

        // Step 1: Check current state
        if ("COMPLETED".equals(currentStatus)) {
            return Mono.error(new SchoolFeeException(
                    "PAYMENT_ALREADY_COMPLETED",
                    "This payment has already been completed. Please check your payment history."));
        }

        if ("PROCESSING".equals(currentStatus)) {
            if (savedPayment.getGatewayTransactionRef() != null) {
                // Gateway already called — return existing authorization
                log.info("Payment already processing: paymentId={}, ref={}",
                        paymentId, savedPayment.getGatewayTransactionRef());
                return buildInitiateResponse(savedPayment, request.paymentMethod());
            } else {
                // Another thread is initializing — wait for it
                log.info("Payment being initialized by another thread: paymentId={}", paymentId);
                return waitForGatewayRef(paymentId, 10)
                        .flatMap(updatedPayment ->
                                buildInitiateResponse(updatedPayment, request.paymentMethod()));
            }
        }

        // Step 2: Status is PENDING or FAILED — try to claim it
        return claimAndProcessCardPayment(gateway, savedPayment, request, customerEmail);
    }

    /**
     * Claim the payment record and call the card gateway.
     */
    private Mono<InitiatePaymentResponse> claimAndProcessCardPayment(
            PaymentGateway gateway,
            Payment payment,
            InitiatePaymentRequest request,
            String customerEmail) {

        UUID paymentId = payment.getId();

        // Atomically update status to PROCESSING
        payment.setStatus("PROCESSING");
        payment.setUpdatedAt(Instant.now());

        return paymentRepository.save(payment)
                .flatMap(claimedPayment -> {
                    log.info("Claimed payment for processing: paymentId={}", paymentId);

                    return gateway.initiatePayment(
                                    paymentId,
                                    customerEmail,
                                    request.amount(),
                                    "School fee payment")
                            .flatMap(gatewayResponse -> {
                                // Gateway call succeeded
                                claimedPayment.setGatewayTransactionRef(
                                        gatewayResponse.gatewayTransactionRef());
                                claimedPayment.setGatewayStatus(gatewayResponse.status());
                                claimedPayment.setUpdatedAt(Instant.now());

                                return paymentRepository.save(claimedPayment)
                                        .thenReturn(new InitiatePaymentResponse(
                                                claimedPayment.getId(),
                                                "PROCESSING",
                                                request.paymentMethod(),
                                                request.amount(),
                                                gatewayResponse.message(),
                                                gatewayResponse.authorizationUrl(),
                                                gatewayResponse.gatewayTransactionRef(),
                                                gatewayResponse.expiresInSeconds()));
                            })
                            .onErrorResume(error -> {
                                // Gateway failed — revert to FAILED
                                log.error("Payment initiation failed: paymentId={}", paymentId, error);
                                return markPaymentFailed(paymentId, error)
                                        .then(Mono.error(error));
                            });
                })
                .onErrorResume(OptimisticLockingFailureException.class, e -> {
                    // Another thread claimed it first — reload and use their result
                    log.info("Optimistic lock failed for payment: {}. Another thread claimed it.", paymentId);
                    return paymentRepository.findById(paymentId)
                            .flatMap(reloaded -> waitForGatewayRef(reloaded.getId(), 10))
                            .flatMap(updatedPayment ->
                                    buildInitiateResponse(updatedPayment, request.paymentMethod()));
                });
    }

    /**
     * Build an InitiatePaymentResponse from an existing payment.
     * Used when the payment was already processed by another thread.
     */
    private Mono<InitiatePaymentResponse> buildInitiateResponse(
            Payment payment, String paymentMethod) {

        return Mono.just(new InitiatePaymentResponse(
                payment.getId(),
                payment.getStatus(),
                paymentMethod,
                payment.getAmount(),
                payment.getStatus().equals("COMPLETED")
                        ? "Payment completed"
                        : "Payment is being processed. Please wait...",
                null, // authorization URL already used
                payment.getGatewayTransactionRef(),
                0)); // Already processing, no expiry
    }

    private Mono<Payment> createPendingPaymentAttempt(
            InitiatePaymentRequest request, UUID schoolId, UUID localUserId, UUID keycloakUserId) {

        UUID idempotencyKey = generateIdempotencyKey(request, localUserId);

        return transactionalOperator.transactional(
                loadPayableFees(request.studentFeeIds(), schoolId, keycloakUserId)
                        .flatMap(payableFees -> {
                            BigDecimal totalAvailable = payableFees.stream()
                                    .map(PayableStudentFee::availableAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            if (request.amount().compareTo(totalAvailable) > 0) {
                                return Mono.error(new SchoolFeeException(
                                        "OVERPAYMENT", "Amount exceeds available balance"));
                            }

                            BigDecimal minAmount = BigDecimal.valueOf(1000);
                            if (request.amount().compareTo(minAmount) < 0) {
                                if (request.amount().compareTo(totalAvailable) != 0) {
                                    return Mono.error(new SchoolFeeException(
                                            "INVALID_PAYMENT_AMOUNT",
                                            "Minimum payment amount is ₦1,000",
                                            "amount"));
                                }
                            }

                            boolean isSingleFee = payableFees.size() == 1;
                            PayableStudentFee firstFee = payableFees.getFirst();

                            Payment payment = Payment.builder()
                                    .id(UUID.randomUUID())
                                    .studentFeeId(isSingleFee ? firstFee.fee().getId() : null)
                                    .studentId(isSingleFee ? firstFee.fee().getStudentId() : null)
                                    .schoolId(schoolId)
                                    .amount(request.amount())
                                    .paymentMethod(request.paymentMethod())
                                    .paymentMode("ONLINE")
                                    .status("PENDING")
                                    .paidBy(localUserId)
                                    .payerPhone(request.phoneNumber())
                                    .idempotencyKey(idempotencyKey.toString())
                                    .createdAt(Instant.now())
                                    .updatedAt(Instant.now())
                                    .build();

                            return paymentRepository.save(payment)
                                    .flatMap(saved -> saveAllocations(
                                            saved.getId(), schoolId, request.amount(), payableFees)
                                            .thenReturn(saved));
                        })
        )
        .onErrorResume(DuplicateKeyException.class, e ->
                paymentRepository.findByIdempotencyKey(idempotencyKey.toString())
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "PAYMENT_ERROR", "Duplicate key but payment not found"))));
    }
    

    private Mono<Void> markPaymentFailed(UUID paymentId, Throwable error) {
        return paymentRepository.findById(paymentId)
                .flatMap(payment -> {
                    payment.setStatus("FAILED");
                    payment.setGatewayStatus("FAILED");
                    payment.setNarration("Gateway initiation failed: " + error.getMessage());
                    payment.setIdempotencyKey("failed-" + UUID.randomUUID());
                    payment.setUpdatedAt(Instant.now());
                    return paymentRepository.save(payment);
                })
                .then();
    }


    // ========================================================================
    // GET PAYMENT STATUS
    // ========================================================================

    @Override
    public Mono<PaymentStatusResponse> getPaymentStatus(UUID paymentId) {
        if (paymentId == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Payment ID is required",
                    "paymentId"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID userId = user.getUserId();
                    UUID schoolId = user.getSchoolId();

                    return paymentRepository.findByIdAndSchoolId(paymentId, schoolId)
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "PAYMENT_NOT_FOUND", "Payment not found")))
                            .flatMap(payment -> verifyPaymentAccess(user, payment)
                                    .thenReturn(payment))
                            .flatMap(payment -> {
                                if ("PENDING".equals(payment.getStatus()) || "PROCESSING".equals(payment.getStatus())) {
                                    return verifyAndUpdatePayment(payment);
                                }
                                return Mono.just(payment);
                            })
                            .flatMap(this::buildPaymentStatusResponse);
                });
    }

    @Override
    public Mono<PaymentStatusResponse> getPaymentStatusByReference(String gatewayTransactionRef) {
        String reference = trimToNull(gatewayTransactionRef);
        if (reference == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Payment reference is required",
                    "reference"));
        }

        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = user.getSchoolId();

                    return paymentRepository.findByGatewayTransactionRef(reference)
                            .filter(payment -> Objects.equals(payment.getSchoolId(), schoolId))
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "PAYMENT_NOT_FOUND", "Payment not found")))
                            .flatMap(payment -> verifyPaymentAccess(user, payment)
                                    .thenReturn(payment))
                            .flatMap(payment -> {
                                if ("PENDING".equals(payment.getStatus()) || "PROCESSING".equals(payment.getStatus())) {
                                    return verifyAndUpdatePayment(payment);
                                }
                                return Mono.just(payment);
                            })
                            .flatMap(this::buildPaymentStatusResponse);
                });
    }

    private Mono<Void> verifyPaymentAccess(
            com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser user, Payment payment) {
        if (!user.isParent()) {
            return Mono.empty();
        }
        return resolveLocalUserId(user.getUserId())
                .filter(localUserId -> Objects.equals(payment.getPaidBy(), localUserId))
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "ACCESS_DENIED", "You can only view your own payments")))
                .then();
    }

    private Mono<Void> expireStuckPayments() {
        Instant beforeTime = Instant.now().minus(Duration.ofMinutes(15));
        Mono<Integer> updateMono = paymentRepository.expireStuckPayments(beforeTime);
        if (updateMono == null) {
            return Mono.empty();
        }
        return updateMono.then();
    }

    private Mono<Payment> verifyAndUpdatePayment(Payment payment) {
        if (payment.getGatewayTransactionRef() == null || payment.getGatewayTransactionRef().isBlank()) {
            // No gateway transaction reference yet. If it is older than 15 minutes, expire it.
            if (payment.getCreatedAt().isBefore(Instant.now().minus(java.time.Duration.ofMinutes(15)))) {
                payment.setStatus("FAILED");
                payment.setGatewayStatus("FAILED");
                payment.setNarration("Payment expired/abandoned");
                payment.setIdempotencyKey("failed-" + UUID.randomUUID());
                payment.setUpdatedAt(Instant.now());
                Mono<Payment> saveMono = paymentRepository.save(payment);
                return saveMono != null ? saveMono : Mono.just(payment);
            }
            return Mono.just(payment);
        }

        PaymentGateway gateway = gatewaySelector.select(payment.getPaymentMethod());
        return gateway.verifyPayment(payment.getGatewayTransactionRef())
                .flatMap(status -> {
                    if (status.isSuccess()) {
                        GatewayCallbackData callbackData = GatewayCallbackData.builder()
                                .gatewayTransactionRef(payment.getGatewayTransactionRef())
                                .gatewayReceiptNumber(status.gatewayReceiptNumber())
                                .amount(status.amount())
                                .phoneNumber(status.phoneNumber())
                                .isSuccess(true)
                                .resultDescription(status.resultDescription())
                                .build();
                        Mono<Void> callbackMono = processGatewayCallback(callbackData);
                        if (callbackMono == null) {
                            return Mono.just(payment);
                        }
                        Mono<Payment> findMono = paymentRepository.findById(payment.getId());
                        return callbackMono.then(findMono != null ? findMono : Mono.just(payment));
                    } else {
                        // If it has expired on gateway/creation, mark as failed
                        if (payment.getCreatedAt().isBefore(Instant.now().minus(java.time.Duration.ofMinutes(15)))) {
                            payment.setStatus("FAILED");
                            payment.setGatewayStatus("FAILED");
                            payment.setNarration("Verification failed/abandoned: " + status.resultDescription());
                            payment.setIdempotencyKey("failed-" + UUID.randomUUID());
                            payment.setUpdatedAt(Instant.now());
                            Mono<Payment> saveMono = paymentRepository.save(payment);
                            return saveMono != null ? saveMono : Mono.just(payment);
                        }
                        return Mono.just(payment);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Failed to verify payment via gateway: reference={}. Error: {}",
                            payment.getGatewayTransactionRef(), error.getMessage());
                    if (payment.getCreatedAt().isBefore(Instant.now().minus(java.time.Duration.ofMinutes(15)))) {
                        payment.setStatus("FAILED");
                        payment.setGatewayStatus("FAILED");
                        payment.setNarration("Verification failed (network/server error)");
                        payment.setIdempotencyKey("failed-" + UUID.randomUUID());
                        payment.setUpdatedAt(Instant.now());
                        Mono<Payment> saveMono = paymentRepository.save(payment);
                        return saveMono != null ? saveMono : Mono.just(payment);
                    }
                    return Mono.just(payment);
                });
    }


    // ========================================================================
    // PAYMENT HISTORY
    // ========================================================================

    @Override
    public Mono<PageResponse<PaymentHistoryResponse>> getPaymentHistory(
            UUID studentId, Pageable pageable) {

        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID userId = user.getUserId();
                    UUID schoolId = user.getSchoolId();
                    int limit = pageable.getPageSize();
                    long offset = (long) pageable.getPageNumber() * limit;

                    if (studentId != null) {
                        Mono<Void> accessCheck = user.isParent()
                                ? verifyParentCanViewStudentFees(userId, schoolId, studentId)
                                : Mono.empty();
                        return accessCheck.then(Mono.defer(() -> pagePaymentHistory(
                                paymentRepository.findByStudentIdAndSchoolIdOrderByCreatedAtDesc(
                                        studentId, schoolId, limit, offset),
                                paymentRepository.countByStudentIdAndSchoolId(studentId, schoolId),
                                pageable.getPageNumber(),
                                limit)));
                    }

                    if (user.isParent()) {
                        return resolveLocalUserId(userId)
                                .flatMap(localUserId -> pagePaymentHistory(
                                        paymentRepository.findByPaidByAndSchoolIdOrderByCreatedAtDesc(
                                                localUserId, schoolId, limit, offset),
                                        paymentRepository.countByPaidByAndSchoolId(localUserId, schoolId),
                                        pageable.getPageNumber(),
                                        limit));
                    }

                    // School staff (Admin, Accountant, etc.) sees school-wide payment history
                    return Mono.defer(() -> pagePaymentHistory(
                            paymentRepository.findBySchoolIdOrderByCreatedAtDesc(
                                    schoolId, limit, offset),
                            paymentRepository.countBySchoolId(schoolId),
                            pageable.getPageNumber(),
                            limit));
                });
    }

    // ========================================================================
    // RECORD OFFLINE PAYMENT
    // ========================================================================

    @Override
    public Mono<OfflinePaymentResponse> recordOfflinePayment(OfflinePaymentRequest request) {
        return Mono.fromCallable(() -> validateAndNormalizeOfflinePaymentRequest(request))
                .flatMap(normalizedRequest -> jwtUtils.getCurrentUser()
                .flatMap(accountant -> {
                    if (!canRecordOfflinePayments(accountant)) {
                        return Mono.error(new SchoolFeeException(
                                "ACCESS_DENIED",
                                "Only school admins and accountants can record offline payments"));
                    }

                    UUID schoolId = accountant.getSchoolId();
                    UUID keycloakUserId = accountant.getUserId();

                    return resolveLocalUserId(keycloakUserId)
                            .flatMap(localUserId -> transactionalOperator.transactional(
                                    studentFeeRepository.findByIdAndSchoolIdForUpdate(
                                                    normalizedRequest.studentFeeId(), schoolId)
                                    .switchIfEmpty(Mono.error(new SchoolFeeException(
                                            "FEE_NOT_FOUND", "Student fee not found")))
                                    .flatMap(fee -> toPayableStudentFee(fee)
                                            .flatMap(payableFee -> {
                                                if (normalizedRequest.amount().compareTo(
                                                        payableFee.availableAmount()) > 0) {
                                                    return Mono.error(new SchoolFeeException(
                                                            "OVERPAYMENT",
                                                            "Amount " + normalizedRequest.amount()
                                                                    + " exceeds available balance "
                                                                    + payableFee.availableAmount(),
                                                            "amount"));
                                                }

                                                Payment payment = buildOfflinePayment(
                                                        normalizedRequest, schoolId, localUserId, fee);

                                                return paymentRepository.save(payment)
                                                        .flatMap(saved -> {
                                                            PaymentAllocation allocation =
                                                                    PaymentAllocation.builder()
                                                                            .schoolId(schoolId)
                                                                            .paymentId(saved.getId())
                                                                            .studentFeeId(normalizedRequest.studentFeeId())
                                                                            .amount(normalizedRequest.amount())
                                                                            .createdAt(Instant.now())
                                                                            .build();
                                                            return allocationRepository.save(allocation)
                                                                    .flatMap(savedAllocation ->
                                                                            createLedgerEntry(savedAllocation, saved)
                                                                                    .thenReturn(savedAllocation))
                                                                    .then(Mono.defer(() -> {
                                                                        if (normalizedRequest.generateReceipt()) {
                                                                            return generateReceipt(saved)
                                                                                    .map(receipt ->
                                                                                            new OfflinePaymentResponse(
                                                                                                    saved.getId(),
                                                                                                    "COMPLETED",
                                                                                                    receipt.getReceiptNumber(),
                                                                                                    normalizedRequest.receivedBy()));
                                                                        }
                                                                        return Mono.just(new OfflinePaymentResponse(
                                                                                saved.getId(), "COMPLETED", null,
                                                                                normalizedRequest.receivedBy()));
                                                                    }));
                                                        });
                                            }))
                            ));
                }));
    }




    @Override
    public Mono<Void> handlePaystackWebhook(String rawPayload) {
        PaymentGateway gateway = gatewaySelector.select("PAYSTACK");

        return gateway.handleCallback(rawPayload)
                .flatMap(this::processGatewayCallback);
    }

    @Override
    public Mono<BankTransferResponse> initiateBankTransfer(BankTransferRequest request) {
        return expireStuckPayments()
                .then(Mono.defer(() -> Mono.fromCallable(() -> validateBankTransferRequest(request))))
                .flatMap(normalizedRequest -> jwtUtils.getCurrentUser()
                        .flatMap(parentUser -> {
                            UUID schoolId = parentUser.getSchoolId();
                            UUID keycloakUserId = parentUser.getUserId();

                            return resolveLocalUserId(keycloakUserId)
                                    .flatMap(localUserId -> {
                                        PaymentGateway gateway = gatewaySelector.select("PAYSTACK");

                                        return gateway.isAvailable(schoolId)
                                                .flatMap(available -> {
                                                    if (!Boolean.TRUE.equals(available)) {
                                                        return Mono.error(new SchoolFeeException(
                                                                "PAYMENT_GATEWAY_UNAVAILABLE",
                                                                "Paystack is not available"));
                                                    }

                                                    return createPendingBankTransferPayment(
                                                            normalizedRequest, schoolId, localUserId, keycloakUserId)
                                                            .flatMap(savedPayment ->
                                                                    processBankTransferWithLock(
                                                                            gateway, savedPayment, request));
                                                });
                                    });
                        }));
    }

    /**
     * Process bank transfer with proper state locking and concurrency control.
     *
     * State machine: PENDING → PROCESSING → COMPLETED | FAILED
     * Uses optimistic locking (version column) to prevent double-processing.
     */
    private Mono<BankTransferResponse> processBankTransferWithLock(
            PaymentGateway gateway, Payment savedPayment, BankTransferRequest request) {

        UUID paymentId = savedPayment.getId();
        String currentStatus = savedPayment.getStatus();

        // Step 1: Check current state
        if ("COMPLETED".equals(currentStatus)) {
            // Already completed — return existing details
            return gateway.resolveBankTransfer(savedPayment.getGatewayTransactionRef());
        }

        if ("PROCESSING".equals(currentStatus)) {
            if (savedPayment.getGatewayTransactionRef() != null) {
                // Gateway already initialized by another thread — resolve details
                log.info("Bank transfer already processing: paymentId={}, ref={}",
                        paymentId, savedPayment.getGatewayTransactionRef());
                return gateway.resolveBankTransfer(savedPayment.getGatewayTransactionRef());
            } else {
                // Another thread is initializing — wait for it
                log.info("Bank transfer being initialized by another thread: paymentId={}", paymentId);
                return waitForGatewayRef(paymentId, 10)
                        .flatMap(updatedPayment ->
                                gateway.resolveBankTransfer(updatedPayment.getGatewayTransactionRef()));
            }
        }

        // Step 2: Status is PENDING or FAILED — try to claim it
        return claimAndProcessPayment(gateway, savedPayment, request);
    }

    /**
     * Claim the payment record by updating status to PROCESSING.
     * Only the thread that succeeds in this update proceeds to call the gateway.
     */
    private Mono<BankTransferResponse> claimAndProcessPayment(
            PaymentGateway gateway, Payment payment, BankTransferRequest request) {

        UUID paymentId = payment.getId();

        // Atomically update status to PROCESSING using optimistic locking
        payment.setStatus("PROCESSING");
        payment.setUpdatedAt(Instant.now());

        return paymentRepository.save(payment)
                .flatMap(claimedPayment -> {
                    // We won the race — call the gateway
                    log.info("Claimed payment for bank transfer: paymentId={}", paymentId);

                    return gateway.initiateBankTransfer(
                                    paymentId,
                                    request.amount(),
                                    request.email(),
                                    request.customerName())
                            .flatMap(transferResponse -> {
                                // Gateway call succeeded — update payment with reference
                                claimedPayment.setGatewayTransactionRef(
                                        transferResponse.reference());
                                claimedPayment.setGatewayStatus(transferResponse.status());
                                claimedPayment.setUpdatedAt(Instant.now());

                                return paymentRepository.save(claimedPayment)
                                        .thenReturn(transferResponse);
                            })
                            .onErrorResume(error -> {
                                // Gateway call failed — revert status to FAILED
                                log.error("Bank transfer initiation failed: paymentId={}", paymentId, error);
                                return markPaymentFailed(paymentId, error)
                                        .then(Mono.error(error));
                            });
                })
                .onErrorResume(OptimisticLockingFailureException.class, e -> {
                    // Another thread updated the payment first — reload and wait
                    log.info("Optimistic lock failed for payment: {}. Another thread claimed it.", paymentId);
                    return paymentRepository.findById(paymentId)
                            .flatMap(reloaded -> waitForGatewayRef(reloaded.getId(), 10))
                            .flatMap(updatedPayment ->
                                    gateway.resolveBankTransfer(updatedPayment.getGatewayTransactionRef()));
                });
    }

    /**
     * Non-blocking retry helper that polls for gatewayTransactionRef.
     * Used when another thread is initializing the payment.
     */
    private Mono<Payment> waitForGatewayRef(UUID paymentId, int remainingRetries) {
        if (remainingRetries <= 0) {
            return Mono.error(new SchoolFeeException(
                    "PAYMENT_TIMEOUT",
                    "Payment initialization timed out. Please check payment status and try again."));
        }

        return paymentRepository.findById(paymentId)
                .flatMap(payment -> {
                    if (payment.getGatewayTransactionRef() != null) {
                        // Reference is now available
                        return Mono.just(payment);
                    }
                    if ("FAILED".equals(payment.getStatus())) {
                        return Mono.error(new SchoolFeeException(
                                "PAYMENT_FAILED",
                                "Payment initialization failed. Please try again."));
                    }
                    // Still waiting — poll again after delay
                    long delayMs = (long) Math.pow(2, 10 - remainingRetries) * 100; // Exponential backoff
                    return Mono.delay(Duration.ofMillis(delayMs))
                            .then(waitForGatewayRef(paymentId, remainingRetries - 1));
                });
    }


    private Mono<Payment> createPendingBankTransferPayment(
            BankTransferRequest request, UUID schoolId, UUID localUserId, UUID keycloakUserId) {

        UUID idempotencyKey = generateIdempotencyKey(request, localUserId);

        return transactionalOperator.transactional(
                loadPayableFees(request.studentFeeIds(), schoolId, keycloakUserId)
                        .flatMap(payableFees -> {
                            BigDecimal totalAvailable = payableFees.stream()
                                    .map(PayableStudentFee::availableAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            if (request.amount().compareTo(totalAvailable) > 0) {
                                return Mono.error(new SchoolFeeException(
                                        "OVERPAYMENT", "Amount exceeds available balance"));
                            }

                            // If single fee, set student_id and student_fee_id for convenience
                            boolean isSingleFee = payableFees.size() == 1;
                            PayableStudentFee firstFee = payableFees.getFirst();

                            Payment payment = Payment.builder()
                                    .id(UUID.randomUUID())
                                    .studentFeeId(isSingleFee ? firstFee.fee().getId() : null)
                                    .studentId(isSingleFee ? firstFee.fee().getStudentId() : null)
                                    .schoolId(schoolId)
                                    .amount(request.amount())
                                    .paymentMethod("BANK_TRANSFER")
                                    .paymentMode("ONLINE")
                                    .status("PENDING")
                                    .paidBy(localUserId)
                                    .idempotencyKey(idempotencyKey.toString())
                                    .createdAt(Instant.now())
                                    .updatedAt(Instant.now())
                                    .build();

                            return paymentRepository.save(payment)
                                    .flatMap(saved -> saveAllocations(
                                            saved.getId(), schoolId, request.amount(), payableFees)
                                            .thenReturn(saved));
                        })
        )
        .onErrorResume(DuplicateKeyException.class, e ->
                paymentRepository.findByIdempotencyKey(idempotencyKey.toString())
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "PAYMENT_ERROR", "Duplicate key but payment not found"))));
    }
    /**
     * Generate a deterministic idempotency key based on the request.
     * Same request content → same key → duplicate detection works.
     */
    private UUID generateIdempotencyKey(InitiatePaymentRequest request, UUID localUserId) {
        String keyMaterial = localUserId.toString()
                + request.studentFeeIds().stream()
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining(","))
                + request.amount().toString()
                + request.paymentMethod();

        return UUID.nameUUIDFromBytes(keyMaterial.getBytes());
    }

    // Same for bank transfer:
    private UUID generateIdempotencyKey(BankTransferRequest request, UUID localUserId) {
        String keyMaterial = localUserId.toString()
                + request.studentFeeIds().stream()
                .sorted()
                .map(UUID::toString)
                .collect(Collectors.joining(","))
                + request.amount().toString()
                + "BANK_TRANSFER";

        return UUID.nameUUIDFromBytes(keyMaterial.getBytes());
    }

    @Override
    public Mono<BankTransferResponse> getBankTransferDetails(UUID paymentId) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> paymentRepository.findByIdAndSchoolId(paymentId, user.getSchoolId())
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "PAYMENT_NOT_FOUND", "Payment not found")))
                        .flatMap(payment -> {
                            if (!"BANK_TRANSFER".equals(payment.getPaymentMethod())) {
                                return Mono.error(new SchoolFeeException(
                                        "INVALID_PAYMENT_METHOD",
                                        "This is not a bank transfer payment"));
                            }

                            if (payment.getGatewayTransactionRef() != null) {
                                PaymentGateway gateway = gatewaySelector.select("PAYSTACK");
                                return gateway.resolveBankTransfer(payment.getGatewayTransactionRef());
                            }

                            return Mono.just(BankTransferResponse.builder()
                                    .reference(payment.getId().toString())
                                    .amount(payment.getAmount())
                                    .status(payment.getStatus())
                                    .message("Transfer details are being generated...")
                                    .build());
                        }));
    }

    private Mono<Void> processGatewayCallback(GatewayCallbackData callbackData) {
        if (!callbackData.isSuccess()) {
            return processFailedOrIgnoredPaystackCallback(callbackData);
        }

        PaystackCallback callback = validateAndNormalizePaystackCallback(callbackData);

        return paymentRepository.findByIdempotencyKey(callback.idempotencyKey())
                .hasElement()
                .flatMap(existing -> {
                    if (Boolean.TRUE.equals(existing)) {
                        log.info("Duplicate Paystack callback ignored: reference={}",
                                callback.gatewayTransactionRef());
                        return Mono.empty();
                    }
                    return processSuccessfulPaystackPayment(callback);
                });
    }

    private Mono<Void> processSuccessfulPaystackPayment(PaystackCallback callback) {
        return transactionalOperator.transactional(
                paymentRepository.findByGatewayTransactionRefForUpdate(callback.gatewayTransactionRef())
                        .flatMap(payment -> {
                            if ("COMPLETED".equals(payment.getStatus())) {
                                return Mono.just(false);
                            }
                            if (!isOpenOnlinePayment(payment)) {
                                return Mono.error(new SchoolFeeException(
                                        "INVALID_PAYMENT_STATE",
                                        "Payment cannot be completed from status: " + payment.getStatus()));
                            }
                            if (callback.amount().compareTo(payment.getAmount()) != 0) {
                                return Mono.error(new SchoolFeeException(
                                        "CALLBACK_AMOUNT_MISMATCH",
                                        "Callback amount does not match the reserved payment amount",
                                        "amount"));
                            }

                            payment.setStatus("COMPLETED");
                            payment.setGatewayStatus("SUCCESS");
                            payment.setIdempotencyKey(callback.idempotencyKey());
                            payment.setNarration("Paystack transaction: " + callback.gatewayReceiptNumber());
                            if (callback.phoneNumber() != null) {
                                payment.setPayerPhone(callback.phoneNumber());
                            }
                            payment.setUpdatedAt(Instant.now());

                            return paymentRepository.save(payment)
                                    .flatMap(saved ->
                                            createLedgerEntries(saved)
                                                    .then(generateReceiptIfAbsent(saved))
                                                    .thenReturn(true));
                        })
                        .switchIfEmpty(alreadyProcessedPaystackCallbackOrError(callback))
        ).then();
    }

    private Mono<Void> processFailedOrIgnoredPaystackCallback(GatewayCallbackData callbackData) {
        String transactionRef = trimToNull(callbackData.gatewayTransactionRef());
        if (transactionRef == null) {
            log.info("Paystack callback ignored: no payment reference. Description: {}",
                    callbackData.resultDescription());
            return Mono.empty();
        }

        PaystackFailureCallback callback = validateAndNormalizePaystackFailureCallback(callbackData);
        return paymentRepository.findByIdempotencyKey(callback.idempotencyKey())
                .hasElement()
                .flatMap(existing -> {
                    if (Boolean.TRUE.equals(existing)) {
                        log.info("Duplicate failed Paystack callback ignored: reference={}",
                                callback.gatewayTransactionRef());
                        return Mono.empty();
                    }
                    return processFailedPaystackPayment(callback);
                });
    }

    private Mono<Void> processFailedPaystackPayment(PaystackFailureCallback callback) {
        return transactionalOperator.transactional(
                paymentRepository.findByGatewayTransactionRefForUpdate(callback.gatewayTransactionRef())
                        .flatMap(payment -> {
                            if ("COMPLETED".equals(payment.getStatus()) || "FAILED".equals(payment.getStatus())) {
                                return Mono.just(false);
                            }
                            if (!isOpenOnlinePayment(payment)) {
                                return Mono.error(new SchoolFeeException(
                                        "INVALID_PAYMENT_STATE",
                                        "Payment cannot be failed from status: " + payment.getStatus()));
                            }

                            payment.setStatus("FAILED");
                            payment.setGatewayStatus("FAILED");
                            payment.setIdempotencyKey(callback.idempotencyKey());
                            payment.setNarration(callback.failureReason());
                            payment.setUpdatedAt(Instant.now());

                            return paymentRepository.save(payment).thenReturn(true);
                        })
                        .switchIfEmpty(alreadyProcessedPaystackFailureOrError(callback))
        ).then();
    }





    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private InitiatePaymentRequest validateAndNormalizeInitiatePaymentRequest(InitiatePaymentRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Payment request is required");
        }
        if (request.studentFeeIds() == null || request.studentFeeIds().isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "At least one fee must be selected",
                    "studentFeeIds");
        }
        List<UUID> feeIds = request.studentFeeIds().stream()
                .peek(feeId -> {
                    if (feeId == null) {
                        throw new SchoolFeeException(
                                "INVALID_PAYMENT_REQUEST",
                                "Fee ID is required",
                                "studentFeeIds");
                    }
                })
                .distinct()
                .sorted()
                .toList();

        String paymentMethod = trimToNull(request.paymentMethod());
        if (paymentMethod == null) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Payment method is required",
                    "paymentMethod");
        }
        String phoneNumber = trimToNull(request.phoneNumber());
        if (phoneNumber == null) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Phone number is required",
                    "phoneNumber");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_AMOUNT",
                    "Amount must be greater than 0",
                    "amount");
        }

        return new InitiatePaymentRequest(
                feeIds,
                paymentMethod.toUpperCase(Locale.ROOT),
                phoneNumber,
                request.amount(),
                request.payOptionalItems());
    }

    /**
     * Validate and normalize a bank transfer request.
     * Ensures all required fields are present and properly formatted.
     */
    private BankTransferRequest validateBankTransferRequest(BankTransferRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Bank transfer request is required");
        }

        // Validate student fee IDs
        if (request.studentFeeIds() == null || request.studentFeeIds().isEmpty()) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "At least one fee must be selected",
                    "studentFeeIds");
        }

        List<UUID> feeIds = request.studentFeeIds().stream()
                .peek(feeId -> {
                    if (feeId == null) {
                        throw new SchoolFeeException(
                                "INVALID_BANK_TRANSFER_REQUEST",
                                "Fee ID cannot be null",
                                "studentFeeIds");
                    }
                })
                .distinct()
                .sorted()
                .toList();

        // Validate amount
        if (request.amount() == null) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_AMOUNT",
                    "Amount is required",
                    "amount");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_AMOUNT",
                    "Amount must be greater than 0",
                    "amount");
        }

        BigDecimal minAmount = BigDecimal.valueOf(1000);
        if (request.amount().compareTo(minAmount) < 0) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_AMOUNT",
                    "Minimum bank transfer amount is ₦1,000",
                    "amount");
        }

        BigDecimal maxAmount = BigDecimal.valueOf(10_000_000);
        if (request.amount().compareTo(maxAmount) > 0) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_AMOUNT",
                    "Maximum bank transfer amount is ₦10,000,000. Please use multiple transfers.",
                    "amount");
        }

        // Validate email (required by Paystack for virtual account generation)
        String email = trimToNull(request.email());
        if (email == null) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Email is required for bank transfer",
                    "email");
        }

        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.\\w{2,}$")) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Invalid email format",
                    "email");
        }

        if (email.length() > 255) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Email must not exceed 255 characters",
                    "email");
        }

        // Validate customer name
        String customerName = trimToNull(request.customerName());
        if (customerName == null) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Customer name is required for bank transfer",
                    "customerName");
        }

        if (customerName.length() < 2) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Customer name must be at least 2 characters",
                    "customerName");
        }

        if (customerName.length() > 200) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Customer name must not exceed 200 characters",
                    "customerName");
        }

        if (!customerName.matches("^[a-zA-Z\\s'\\-]+$")) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Customer name contains invalid characters. Only letters, spaces, hyphens, and apostrophes are allowed.",
                    "customerName");
        }

        // Validate against fee IDs from loaded payable fees (done later, but quick sanity check)
        if (feeIds.size() > 20) {
            throw new SchoolFeeException(
                    "INVALID_BANK_TRANSFER_REQUEST",
                    "Cannot pay for more than 20 fees in a single transfer",
                    "studentFeeIds");
        }

        return new BankTransferRequest(
                feeIds,
                request.amount(),
                email,
                customerName);
    }



    private OfflinePaymentRequest validateAndNormalizeOfflinePaymentRequest(OfflinePaymentRequest request) {
        if (request == null) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Offline payment request is required");
        }
        if (request.studentFeeId() == null) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Student fee ID is required",
                    "studentFeeId");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_AMOUNT",
                    "Amount must be greater than 0",
                    "amount");
        }
        String paymentMethod = trimToNull(request.paymentMethod());
        if (paymentMethod == null) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Payment method is required",
                    "paymentMethod");
        }
        if (request.paymentDate() == null) {
            throw new SchoolFeeException(
                    "INVALID_PAYMENT_REQUEST",
                    "Payment date is required",
                    "paymentDate");
        }

        return new OfflinePaymentRequest(
                request.studentFeeId(),
                request.amount(),
                paymentMethod.toUpperCase(Locale.ROOT),
                request.paymentDate(),
                trimToNull(request.receivedBy()),
                trimToNull(request.notes()),
                request.generateReceipt());
    }

    private PaystackCallback validateAndNormalizePaystackCallback(GatewayCallbackData callbackData) {
        String transactionRef = trimToNull(callbackData.gatewayTransactionRef());
        if (transactionRef == null) {
            throw new SchoolFeeException(
                    "INVALID_CALLBACK",
                    "Paystack reference is required",
                    "reference");
        }

        BigDecimal amount = Optional.ofNullable(callbackData.amount()).orElse(BigDecimal.ZERO);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SchoolFeeException(
                    "INVALID_CALLBACK",
                    "Callback amount must be greater than 0",
                    "amount");
        }

        String receiptNumber = Optional.ofNullable(trimToNull(callbackData.gatewayReceiptNumber()))
                .orElse(transactionRef);
        String idempotencySource = "paystack-success:" + transactionRef + ":" + receiptNumber;

        return new PaystackCallback(
                transactionRef,
                receiptNumber,
                amount,
                trimToNull(callbackData.phoneNumber()),
                UUID.nameUUIDFromBytes(idempotencySource.getBytes(StandardCharsets.UTF_8)).toString());
    }

    private PaystackFailureCallback validateAndNormalizePaystackFailureCallback(
            GatewayCallbackData callbackData) {
        String transactionRef = trimToNull(callbackData.gatewayTransactionRef());
        if (transactionRef == null) {
            throw new SchoolFeeException(
                    "INVALID_CALLBACK",
                    "Paystack reference is required",
                    "reference");
        }

        String failureReason = Optional.ofNullable(trimToNull(callbackData.resultDescription()))
                .orElse("Paystack payment failed");
        String idempotencySource = "paystack-failure:" + transactionRef + ":" + failureReason;

        return new PaystackFailureCallback(
                transactionRef,
                failureReason,
                UUID.nameUUIDFromBytes(idempotencySource.getBytes(StandardCharsets.UTF_8)).toString());
    }

    private Mono<Void> verifyParentCanViewStudentFees(UUID parentUserId, UUID schoolId, UUID studentId) {
        return guardianLinkRepository
                .findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(parentUserId, studentId, schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "ACCESS_DENIED",
                        "You do not have access to view this student's payment history",
                        "studentId")))
                .then();
    }

    private Mono<List<PayableStudentFee>> loadPayableFees(
            List<UUID> feeIds, UUID schoolId, UUID parentUserId) {
        return Flux.fromIterable(feeIds)
                .concatMap(feeId -> studentFeeRepository.findByIdAndSchoolIdForUpdate(feeId, schoolId)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "FEE_NOT_FOUND",
                                "Fee not found: " + feeId,
                                "studentFeeIds")))
                        .flatMap(fee -> verifyParentCanPayFee(parentUserId, schoolId, fee)
                                .then(toPayableStudentFee(fee))))
                .collectList()
                .flatMap(payableFees -> {
                    if (payableFees.isEmpty()) {
                        return Mono.error(new SchoolFeeException(
                                "FEE_NOT_FOUND",
                                "No fees found for payment",
                                "studentFeeIds"));
                    }
                    return Mono.just(payableFees);
                });
    }

    private Payment buildOfflinePayment(
            OfflinePaymentRequest request, UUID schoolId, UUID userId, StudentFee fee) {
        Instant now = Instant.now();
        return Payment.builder()
                .id(UUID.randomUUID())
                .studentFeeId(request.studentFeeId())
                .studentId(fee.getStudentId())
                .schoolId(schoolId)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .paymentGateway("OFFLINE")
                .paymentMode("OFFLINE")
                .status("COMPLETED")
                .offlineApprovedBy(userId)
                .offlineApprovalDate(now)
                .paidBy(userId)
                .payerName(request.receivedBy())
                .narration(request.notes())
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(request.paymentDate())
                .updatedAt(now)
                .build();
    }

    private Mono<Void> verifyParentCanPayFee(UUID parentUserId, UUID schoolId, StudentFee fee) {
        return guardianLinkRepository
                .findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(
                        parentUserId, fee.getStudentId(), schoolId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "ACCESS_DENIED",
                        "You do not have access to pay this student's fees",
                        "studentFeeIds")))
                .then();
    }

    private Mono<PayableStudentFee> toPayableStudentFee(StudentFee fee) {
        Mono<BigDecimal> currentBalanceMono = ledgerEntryRepository
                .findByStudentFeeIdOrderByCreatedAtAsc(fee.getId())
                .collectList()
                .map(entries -> currentBalance(fee, entries));

        Mono<BigDecimal> reservedAmountMono = allocationRepository
                .sumActiveAllocatedAmount(fee.getId(), fee.getSchoolId())
                .defaultIfEmpty(BigDecimal.ZERO);

        return Mono.zip(currentBalanceMono, reservedAmountMono)
                .flatMap(tuple -> {
                    BigDecimal currentBalance = tuple.getT1();
                    BigDecimal reservedAmount = tuple.getT2();
                    BigDecimal availableAmount = currentBalance.subtract(reservedAmount);

                    if (currentBalance.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.error(new SchoolFeeException(
                                "FEE_ALREADY_PAID",
                                "Fee is already fully paid: " + fee.getId(),
                                "studentFeeIds"));
                    }
                    if (availableAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        return Mono.error(new SchoolFeeException(
                                "PAYMENT_IN_PROGRESS",
                                "A payment is already in progress for fee: " + fee.getId(),
                                "studentFeeIds"));
                    }
                    return Mono.just(new PayableStudentFee(
                            fee, currentBalance, reservedAmount, availableAmount));
                });
    }

    private BigDecimal currentBalance(StudentFee fee, List<LedgerEntry> entries) {
        if (!entries.isEmpty()) {
            LedgerEntry lastEntry = entries.getLast();
            if (lastEntry.getBalanceAfter() != null) {
                return lastEntry.getBalanceAfter().max(BigDecimal.ZERO);
            }
            return entries.stream()
                    .map(entry -> Optional.ofNullable(entry.getAmount()).orElse(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .max(BigDecimal.ZERO);
        }
        return Optional.ofNullable(fee.getTotalAmount()).orElse(BigDecimal.ZERO)
                .subtract(Optional.ofNullable(fee.getDiscountAmount()).orElse(BigDecimal.ZERO))
                .add(Optional.ofNullable(fee.getLateFeeAmount()).orElse(BigDecimal.ZERO))
                .max(BigDecimal.ZERO);
    }

    private Mono<Void> saveAllocations(
            UUID paymentId,
            UUID schoolId,
            BigDecimal totalAmount,
            List<PayableStudentFee> payableFees) {

        BigDecimal remaining = totalAmount;
        List<PaymentAllocation> allocations = new ArrayList<>();

        for (PayableStudentFee payableFee : payableFees) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal allocate = payableFee.availableAmount().min(remaining);
            if (allocate.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(PaymentAllocation.builder()
                        .schoolId(schoolId)
                        .paymentId(paymentId)
                        .studentFeeId(payableFee.fee().getId())
                        .amount(allocate)
                        .createdAt(Instant.now())
                        .build());
                remaining = remaining.subtract(allocate);
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            return Mono.error(new SchoolFeeException(
                    "OVERPAYMENT",
                    "Amount exceeds available balance",
                    "amount"));
        }

        return Flux.fromIterable(allocations)
                .concatMap(allocationRepository::save)
                .then();
    }

    private Mono<Void> createLedgerEntries(Payment payment) {
        return allocationRepository.findByPaymentId(payment.getId())
                .concatMap(allocation -> createLedgerEntry(allocation, payment))
                .then();
    }

    private Mono<Void> createLedgerEntry(PaymentAllocation allocation, Payment payment) {
        return studentFeeRepository.findByIdAndSchoolIdForUpdate(
                        allocation.getStudentFeeId(), payment.getSchoolId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "FEE_NOT_FOUND",
                        "Fee not found: " + allocation.getStudentFeeId())))
                .flatMap(fee -> ledgerEntryRepository
                        .findTopByStudentFeeIdOrderByCreatedAtDesc(allocation.getStudentFeeId())
                        .defaultIfEmpty(LedgerEntry.builder()
                                .balanceAfter(currentBalance(fee, Collections.emptyList()))
                                .build())
                        .flatMap(lastEntry -> {
                            BigDecimal balanceBefore = Optional.ofNullable(lastEntry.getBalanceAfter())
                                    .orElse(currentBalance(fee, Collections.emptyList()));
                            if (allocation.getAmount().compareTo(balanceBefore) > 0) {
                                return Mono.error(new SchoolFeeException(
                                        "OVERPAYMENT",
                                        "Payment allocation exceeds current fee balance",
                                        "amount"));
                            }

                            BigDecimal newBalance = balanceBefore.subtract(allocation.getAmount())
                                    .max(BigDecimal.ZERO);

                            LedgerEntry entry = LedgerEntry.builder()
                                    .id(UUID.randomUUID())
                                    .studentFeeId(allocation.getStudentFeeId())
                                    .studentId(fee.getStudentId())
                                    .schoolId(payment.getSchoolId())
                                    .entryType("PAYMENT")
                                    .amount(allocation.getAmount().negate())
                                    .balanceAfter(newBalance)
                                    .sourceEntityType("payment")
                                    .sourceEntityId(payment.getId())
                                    .description(payment.getPaymentMethod() + " payment: " +
                                            payment.getGatewayTransactionRef())
                                    .transactionDate(paymentTransactionDate(payment))
                                    .recordedBy(payment.getPaidBy())
                                    .systemAction(payment.getPaymentMode().equals("OFFLINE")
                                            ? "OFFLINE_PAYMENT" : "PAYSTACK_CALLBACK")
                                    .idempotencyKey(ledgerPaymentIdempotencyKey(
                                            payment.getId(), allocation.getStudentFeeId()))
                                    .build();

                            return ledgerEntryRepository.save(entry).then();
                        }));
    }

    private Mono<Receipt> generateReceipt(Payment payment) {
        String receiptNumber = generateReceiptNumber();

        Receipt receipt = Receipt.builder()
                .id(UUID.randomUUID())
                .paymentId(payment.getId())
                .receiptNumber(receiptNumber)
                .studentId(payment.getStudentId())
                .schoolId(payment.getSchoolId())
                .amount(payment.getAmount())
                .paymentDate(paymentTransactionDate(payment))
                .paymentMethod(payment.getPaymentMethod())
                .paidBy(payment.getPaidBy())
                .generatedBy(payment.getPaidBy())
                .smsSent(false)
                .emailSent(false)
                .createdAt(Instant.now())
                .build();

        return receiptRepository.save(receipt);
    }

    private String generateReceiptNumber() {
        String year = String.valueOf(LocalDate.now().getYear());
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "RCP-" + year + "-" + random;
    }

    private UUID ledgerPaymentIdempotencyKey(UUID paymentId, UUID studentFeeId) {
        return UUID.nameUUIDFromBytes(
                ("payment:" + paymentId + ":" + studentFeeId).getBytes(StandardCharsets.UTF_8));
    }

    private Mono<PaymentStatusResponse> buildPaymentStatusResponse(Payment payment) {
        Mono<List<PaymentStatusResponse.BreakdownItem>> breakdownMono =
                allocationRepository.findByPaymentId(payment.getId())
                .flatMap(allocation ->
                        studentFeeRepository.findById(allocation.getStudentFeeId())
                                .flatMap(fee ->
                                        studentRepository.findById(fee.getStudentId())
                                                .map(student -> new PaymentStatusResponse.BreakdownItem(
                                                        student.getFirstName() + " " + student.getLastName(),
                                                        student.getAdmissionNumber(),
                                                        "Fee payment", // Phase 2: structure name
                                                        allocation.getAmount()))
                                                .defaultIfEmpty(new PaymentStatusResponse.BreakdownItem(
                                                        "Unknown student",
                                                        "",
                                                        "Fee payment",
                                                        allocation.getAmount())))
                )
                .collectList();

        Mono<Optional<PaymentStatusResponse.ReceiptInfo>> receiptMono =
                receiptRepository.findByPaymentId(payment.getId())
                        .map(receipt -> Optional.of(new PaymentStatusResponse.ReceiptInfo(
                                receipt.getReceiptNumber(),
                                "/api/v1/receipts/" + receipt.getReceiptNumber() + "/pdf",
                                List.<PaymentStatusResponse.BreakdownItem>of())))
                        .defaultIfEmpty(Optional.empty());

        return Mono.zip(breakdownMono, receiptMono)
                .map(tuple -> {
                    List<PaymentStatusResponse.BreakdownItem> breakdown = tuple.getT1();
                    PaymentStatusResponse.ReceiptInfo receiptInfo = tuple.getT2()
                            .map(receipt -> new PaymentStatusResponse.ReceiptInfo(
                                    receipt.receiptNumber(),
                                    receipt.receiptUrl(),
                                    breakdown))
                            .orElse(null);

                    return new PaymentStatusResponse(
                            payment.getId(),
                            payment.getStatus(),
                            payment.getAmount(),
                            payment.getPaymentMethod(),
                            payment.getGatewayTransactionRef(),
                            payment.getUpdatedAt(),
                            receiptInfo);
                });
    }

    private Mono<PageResponse<PaymentHistoryResponse>> pagePaymentHistory(
            Flux<Payment> paymentsFlux, Mono<Long> totalElementsMono, int page, int limit) {
        return Mono.zip(
                        paymentsFlux.flatMap(this::toPaymentHistoryResponse).collectList(),
                        totalElementsMono.defaultIfEmpty(0L))
                .map(tuple -> new PageResponse<>(
                        tuple.getT1(), page, limit,
                        tuple.getT2(), totalPages(tuple.getT2(), limit)));
    }

    private Mono<PaymentHistoryResponse> toPaymentHistoryResponse(Payment payment) {
        Mono<String> descMono;
        if (payment.getNarration() != null) {
            descMono = Mono.just(payment.getNarration());
        } else if (payment.getStudentFeeId() == null) {
            descMono = Mono.just("Fee payment");
        } else {
            var feeMono = studentFeeRepository != null ? studentFeeRepository.findById(payment.getStudentFeeId()) : null;
            if (feeMono == null) {
                descMono = Mono.just("Fee payment");
            } else {
                descMono = feeMono
                        .flatMap(fee -> {
                            var structMono = feeStructureRepository != null ? feeStructureRepository.findById(fee.getFeeStructureId()) : null;
                            return structMono != null ? structMono : Mono.empty();
                        })
                        .map(structure -> "Payment for " + structure.getName())
                        .defaultIfEmpty("Fee payment");
            }
        }

        return descMono.flatMap(desc -> receiptRepository.findByPaymentId(payment.getId())
                .map(receipt -> new PaymentHistoryResponse(
                        payment.getId(),
                        payment.getCreatedAt(),
                        payment.getAmount(),
                        payment.getPaymentMethod(),
                        payment.getStatus(),
                        desc,
                        receipt.getReceiptNumber()))
                .defaultIfEmpty(new PaymentHistoryResponse(
                        payment.getId(),
                        payment.getCreatedAt(),
                        payment.getAmount(),
                        payment.getPaymentMethod(),
                        payment.getStatus(),
                        desc,
                        null)));
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Mono<Void> generateReceiptIfAbsent(Payment payment) {
        return receiptRepository.findByPaymentId(payment.getId())
                .hasElement()
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.empty()
                        : generateReceipt(payment).then());
    }

    private Mono<Boolean> alreadyProcessedPaystackCallbackOrError(PaystackCallback callback) {
        return paymentRepository.findByIdempotencyKey(callback.idempotencyKey())
                .hasElement()
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.just(false)
                        : Mono.error(new SchoolFeeException(
                                "PAYMENT_NOT_FOUND",
                                "No pending payment found for: " + callback.gatewayTransactionRef())));
    }

    private Mono<Boolean> alreadyProcessedPaystackFailureOrError(PaystackFailureCallback callback) {
        return paymentRepository.findByIdempotencyKey(callback.idempotencyKey())
                .hasElement()
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.just(false)
                        : Mono.error(new SchoolFeeException(
                                "PAYMENT_NOT_FOUND",
                                "No pending payment found for: " + callback.gatewayTransactionRef())));
    }

    private boolean isOpenOnlinePayment(Payment payment) {
        return "ONLINE".equals(payment.getPaymentMode())
                && ("PENDING".equals(payment.getStatus()) || "PROCESSING".equals(payment.getStatus()));
    }

    private boolean canRecordOfflinePayments(com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser user) {
        return user != null && (user.isSuperAdmin() || user.isSchoolAdmin() || user.isAccountant());
    }

    private int totalPages(long totalElements, int size) {
        if (size <= 0 || totalElements <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalElements / size);
    }

    private Instant paymentTransactionDate(Payment payment) {
        if ("OFFLINE".equals(payment.getPaymentMode())) {
            return Optional.ofNullable(payment.getCreatedAt()).orElse(Instant.now());
        }
        return Optional.ofNullable(payment.getUpdatedAt()).orElse(Instant.now());
    }

    private record PaystackCallback(
            String gatewayTransactionRef,
            String gatewayReceiptNumber,
            BigDecimal amount,
            String phoneNumber,
            String idempotencyKey) {
    }

    private record PaystackFailureCallback(
            String gatewayTransactionRef,
            String failureReason,
            String idempotencyKey) {
    }

    private record PayableStudentFee(
            StudentFee fee,
            BigDecimal currentBalance,
            BigDecimal reservedAmount,
            BigDecimal availableAmount) {
    }
}
