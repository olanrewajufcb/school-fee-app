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
import com.fee.app.schoolfeeapp.payment.dto.request.InitiatePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.request.OfflinePaymentRequest;
import com.fee.app.schoolfeeapp.payment.dto.response.InitiatePaymentResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.OfflinePaymentResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.PaymentHistoryResponse;
import com.fee.app.schoolfeeapp.payment.dto.response.PaymentStatusResponse;
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
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

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
                .then(Mono.defer(() -> Mono.fromCallable(() -> validateAndNormalizeInitiatePaymentRequest(request))))
                .flatMap(normalizedRequest -> jwtUtils.getCurrentUser()
                .flatMap(parentUser -> {
                    if (!parentUser.isParent()) {
                        return Mono.error(new SchoolFeeException(
                                "ACCESS_DENIED", "Only parents can make payments"));
                    }

                    UUID schoolId = parentUser.getSchoolId();
                    UUID keycloakUserId = parentUser.getUserId();
                    PaymentGateway gateway = gatewaySelector.select(normalizedRequest.paymentMethod());

                    return resolveLocalUserId(keycloakUserId)
                            .flatMap(localUserId -> gateway.isAvailable(schoolId)
                                    .flatMap(available -> {
                                        if (!Boolean.TRUE.equals(available)) {
                                            return Mono.error(new SchoolFeeException(
                                                    "PAYMENT_GATEWAY_UNAVAILABLE",
                                                    "Payment gateway is not available for your school"));
                                        }

                                        return createPendingPaymentAttempt(normalizedRequest, schoolId, localUserId, keycloakUserId)
                                                .flatMap(savedPayment -> initiateGatewayPayment(
                                                        gateway, normalizedRequest, savedPayment));
                                    }));
                }));
    }

    private Mono<Payment> createPendingPaymentAttempt(
            InitiatePaymentRequest request, UUID schoolId, UUID localUserId, UUID keycloakUserId) {
        return transactionalOperator.transactional(
                loadPayableFees(request.studentFeeIds(), schoolId, keycloakUserId)
                        .flatMap(payableFees -> {
                            BigDecimal totalAvailable = payableFees.stream()
                                    .map(PayableStudentFee::availableAmount)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            if (request.amount().compareTo(totalAvailable) > 0) {
                                return Mono.error(new SchoolFeeException(
                                        "OVERPAYMENT",
                                        "Amount " + request.amount() + " exceeds available balance " + totalAvailable,
                                        "amount"));
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

                            StudentFee firstFee = payableFees.getFirst().fee();
                            Payment payment = buildPayment(request, schoolId, localUserId, firstFee);

                            return paymentRepository.save(payment)
                                    .flatMap(saved -> saveAllocations(
                                            saved.getId(), schoolId, request.amount(), payableFees)
                                            .thenReturn(saved));
                        })
        );
    }

    private Mono<InitiatePaymentResponse> initiateGatewayPayment(
            PaymentGateway gateway, InitiatePaymentRequest request, Payment savedPayment) {
        return gateway.initiatePayment(
                        savedPayment.getId(),
                        request.phoneNumber(),
                        request.amount(),
                        "School fee payment")
                .flatMap(gatewayResponse -> {
                    savedPayment.setGatewayTransactionRef(gatewayResponse.gatewayTransactionRef());
                    savedPayment.setGatewayStatus(gatewayResponse.status());
                    savedPayment.setStatus("PROCESSING");
                    savedPayment.setUpdatedAt(Instant.now());
                    return paymentRepository.save(savedPayment)
                            .thenReturn(new InitiatePaymentResponse(
                                    savedPayment.getId(),
                                    "PROCESSING",
                                    request.paymentMethod(),
                                    request.amount(),
                                    gatewayResponse.message(),
                                    gatewayResponse.authorizationUrl(),
                                    gatewayResponse.gatewayTransactionRef(),
                                    gatewayResponse.expiresInSeconds()));
                })
                .onErrorResume(error -> markPaymentFailed(savedPayment.getId(), error)
                        .then(Mono.error(error)));
    }

    private Mono<Void> markPaymentFailed(UUID paymentId, Throwable error) {
        return paymentRepository.findById(paymentId)
                .flatMap(payment -> {
                    payment.setStatus("FAILED");
                    payment.setGatewayStatus("FAILED");
                    payment.setNarration("Gateway initiation failed: " + error.getMessage());
                    payment.setUpdatedAt(Instant.now());
                    return paymentRepository.save(payment);
                })
                .then();
    }

    private Payment buildPayment(
            InitiatePaymentRequest request, UUID schoolId, UUID userId, StudentFee firstFee) {
        return Payment.builder()
                .id(UUID.randomUUID())
                .studentFeeId(firstFee.getId())
                .studentId(firstFee.getStudentId())
                .schoolId(schoolId)
                .amount(request.amount())
                .paymentMethod(request.paymentMethod())
                .paymentGateway(request.paymentMethod())
                .paymentMode("ONLINE")
                .status("PENDING")
                .paidBy(userId)
                .payerPhone(request.phoneNumber())
                .idempotencyKey(UUID.randomUUID().toString())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
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
                            .filter(payment -> !user.isParent()
                                    || Objects.equals(payment.getPaidBy(), userId))
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "ACCESS_DENIED", "You can only view your own payments")))
                            .flatMap(payment -> {
                                if ("PENDING".equals(payment.getStatus()) || "PROCESSING".equals(payment.getStatus())) {
                                    return verifyAndUpdatePayment(payment);
                                }
                                return Mono.just(payment);
                            })
                            .flatMap(this::buildPaymentStatusResponse);
                });
    }

    private Mono<Void> expireStuckPayments() {
        Instant beforeTime = Instant.now().minus(java.time.Duration.ofMinutes(15));
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
