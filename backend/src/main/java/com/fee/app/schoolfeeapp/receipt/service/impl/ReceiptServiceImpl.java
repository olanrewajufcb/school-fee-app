package com.fee.app.schoolfeeapp.receipt.service.impl;


import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import com.fee.app.schoolfeeapp.payment.domain.Receipt;
import com.fee.app.schoolfeeapp.payment.repository.PaymentAllocationRepository;
import com.fee.app.schoolfeeapp.payment.repository.PaymentRepository;
import com.fee.app.schoolfeeapp.payment.repository.ReceiptRepository;
import com.fee.app.schoolfeeapp.receipt.dto.request.ShareReceiptRequest;
import com.fee.app.schoolfeeapp.receipt.dto.response.ReceiptDetailResponse;
import com.fee.app.schoolfeeapp.receipt.dto.response.ShareReceiptResponse;
import com.fee.app.schoolfeeapp.receipt.service.ReceiptService;
import com.fee.app.schoolfeeapp.receipt.utils.ReceiptPdfGenerator;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final StudentFeeRepository studentFeeRepository;
    private final StudentRepository studentRepository;
    private final SchoolRepository schoolRepository;
    private final ReceiptPdfGenerator pdfGenerator;
    private final SmsService smsService;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    public Mono<ReceiptDetailResponse> getReceiptDetails(String receiptNumber) {
        return Mono.fromCallable(() -> validateReceiptNumber(receiptNumber))
                .flatMap(this::loadAuthorizedReceiptContext)
                .flatMap(this::buildReceiptDetailResponse);
    }

    @Override
    public Mono<DataBuffer> downloadReceiptPdf(String receiptNumber) {
        return getReceiptDetails(receiptNumber)
                .map(pdfGenerator::generatePdf)
                .map(pdfBytes -> new DefaultDataBufferFactory().wrap(pdfBytes));
    }

    @Override
    public Mono<ShareReceiptResponse> shareReceipt(String receiptNumber, ShareReceiptRequest request) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> {
                    UUID schoolId = user.getSchoolId();

                    return receiptRepository.findByReceiptNumber(receiptNumber)
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "RECEIPT_NOT_FOUND",
                                    "Receipt not found: " + receiptNumber)))
                            .filter(r -> r.getSchoolId().equals(schoolId))
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "RECEIPT_NOT_IN_SCHOOL",
                                    "Receipt does not belong to your school")))
                            .flatMap(receipt -> {
                                if ("SMS".equalsIgnoreCase(request.channel())) {
                                    return shareViaSms(receipt, request.recipient());
                                } else if ("EMAIL".equalsIgnoreCase(request.channel())) {
                                    return shareViaEmail(receipt, request.recipient());
                                } else {
                                    return shareViaWhatsApp(receipt, request.recipient());
                                }
                            });
                });
    }

    // ========================================================================
    // PRIVATE HELPERS
    // ========================================================================

    private Mono<ReceiptContext> loadAuthorizedReceiptContext(String receiptNumber) {
        return jwtUtils.getCurrentUser()
                .flatMap(user -> receiptRepository.findByReceiptNumber(receiptNumber)
                        .switchIfEmpty(Mono.error(new SchoolFeeException(
                                "RECEIPT_NOT_FOUND",
                                "Receipt not found: " + receiptNumber)))
                        .flatMap(receipt -> loadReceiptContextForUser(receipt, user)));
    }

    private Mono<UUID> resolveLocalUserId(UUID keycloakUserId) {
        return userRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakUserId)
                .map(User::getId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "USER_NOT_FOUND",
                        "User not found in the database")));
    }

    private Mono<ReceiptContext> loadReceiptContextForUser(Receipt receipt, SchoolFeeUser user) {
        if (receipt.getSchoolId() == null || receipt.getPaymentId() == null) {
            return Mono.error(new SchoolFeeException(
                    "INVALID_RECEIPT_STATE",
                    "Receipt is missing required payment or school information"));
        }

        if (!user.isSuperAdmin()
                && (user.getSchoolId() == null || !receipt.getSchoolId().equals(user.getSchoolId()))) {
            return Mono.error(new SchoolFeeException(
                    "RECEIPT_NOT_IN_SCHOOL",
                    "Receipt does not belong to your school"));
        }

        return paymentRepository.findByIdAndSchoolId(receipt.getPaymentId(), receipt.getSchoolId())
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "PAYMENT_NOT_FOUND",
                        "Payment for receipt not found: " + receipt.getReceiptNumber())))
                .flatMap(payment -> {
                    Mono<Payment> authCheck = Mono.just(payment);
                    if (user.isParent()) {
                        authCheck = resolveLocalUserId(user.getUserId())
                                .flatMap(localUserId -> {
                                    if (!Objects.equals(payment.getPaidBy(), localUserId)) {
                                        return Mono.error(new SchoolFeeException(
                                                "ACCESS_DENIED",
                                                "You can only view receipts for payments you made"));
                                    }
                                    return Mono.just(payment);
                                });
                    }
                    return authCheck.flatMap(p -> schoolRepository.findById(receipt.getSchoolId())
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "SCHOOL_NOT_FOUND",
                                    "School for receipt not found")))
                            .map(school -> new ReceiptContext(receipt, p, school)));
                });
    }

    private Mono<ReceiptDetailResponse> buildReceiptDetailResponse(ReceiptContext context) {
        Receipt receipt = context.receipt();
        Payment payment = context.payment();
        School school = context.school();

        BigDecimal amount = Optional.ofNullable(receipt.getAmount())
                .orElse(Optional.ofNullable(payment.getAmount()).orElse(BigDecimal.ZERO));
        String amountInWords = Optional.ofNullable(trimToNull(receipt.getAmountInWords()))
                .orElseGet(() -> convertAmountToWords(amount));
        String paymentMethod = Optional.ofNullable(trimToNull(receipt.getPaymentMethod()))
                .orElse(payment.getPaymentMethod());
        Instant paymentDate = Optional.ofNullable(receipt.getPaymentDate())
                .orElse(Optional.ofNullable(payment.getUpdatedAt())
                        .orElse(payment.getCreatedAt()));
        Instant generatedAt = Optional.ofNullable(receipt.getReceiptGeneratedAt())
                .orElse(receipt.getCreatedAt());
        String paidBy = Optional.ofNullable(trimToNull(receipt.getPaidByName()))
                .orElseGet(() -> Optional.ofNullable(trimToNull(payment.getPayerName()))
                        .orElse("Parent"));

        return buildBreakdown(payment.getId())
                .map(breakdown -> new ReceiptDetailResponse(
                        receipt.getReceiptNumber(),
                        payment.getId(),
                        school.getName(),
                        formatSchoolAddress(school),
                        paidBy,
                        amount,
                        amountInWords,
                        paymentMethod,
                        paymentDate,
                        breakdown,
                        generatedAt,
                        Boolean.TRUE.equals(receipt.getSmsSent()),
                        Boolean.TRUE.equals(receipt.getEmailSent())
                ));
    }

    private Mono<List<ReceiptDetailResponse.BreakdownItem>> buildBreakdown(UUID paymentId) {
        return allocationRepository.findByPaymentId(paymentId)
                .flatMap(allocation ->
                        studentFeeRepository.findById(allocation.getStudentFeeId())
                                .flatMap(fee ->
                                        studentRepository.findById(fee.getStudentId())
                                                .map(student -> new ReceiptDetailResponse.BreakdownItem(
                                                        student.getFirstName() + " " + student.getLastName(),
                                                        student.getAdmissionNumber(),
                                                        null, // Phase 2: className
                                                        null, // Phase 2: term
                                                        allocation.getAmount()
                                                ))
                                )
                )
                .collectList()
                .defaultIfEmpty(List.of());
    }

    private Mono<ShareReceiptResponse> shareViaSms(Receipt receipt, String recipient) {
        String message = String.format(
                "SchoolFee Receipt %s: ₦%,.2f paid. Download: %s/api/v1/receipts/%s/pdf",
                receipt.getReceiptNumber(),
                receipt.getAmount(),
                "https://api.schoolfee.app",
                receipt.getReceiptNumber()
        );

        return smsService.send(recipient, message)
                .thenReturn(new ShareReceiptResponse(
                        "SMS",
                        Instant.now(),
                        "Receipt " + receipt.getReceiptNumber() + " sent to " + recipient
                ));
    }

    private Mono<ShareReceiptResponse> shareViaEmail(Receipt receipt, String recipient) {
        // Phase 2: Email integration
        return Mono.just(new ShareReceiptResponse(
                "EMAIL",
                Instant.now(),
                "Receipt " + receipt.getReceiptNumber() + " emailed to " + recipient
        ));
    }

    private Mono<ShareReceiptResponse> shareViaWhatsApp(Receipt receipt, String recipient) {
        // Phase 2: WhatsApp Business API integration
        return Mono.just(new ShareReceiptResponse(
                "WHATSAPP",
                Instant.now(),
                "Receipt " + receipt.getReceiptNumber() + " shared via WhatsApp to " + recipient
        ));
    }

    private String formatSchoolAddress(School school) {
        StringBuilder sb = new StringBuilder();
        if (school.getAddress() != null) sb.append(school.getAddress());
        if (school.getCity() != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(school.getCity());
        }
        if (school.getState() != null) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(school.getState());
        }
        return sb.toString();
    }

    private String validateReceiptNumber(String receiptNumber) {
        String normalized = trimToNull(receiptNumber);
        if (normalized == null) {
            throw new SchoolFeeException(
                    "INVALID_RECEIPT_REQUEST",
                    "Receipt number is required",
                    "receiptNumber");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String convertAmountToWords(BigDecimal amount) {
        // Phase 2: Proper amount-to-words conversion
        // MVP: Simple representation
        long naira = amount.longValue();
        return String.format("%,d Naira Only", naira);
    }

    private record ReceiptContext(Receipt receipt, Payment payment, School school) {
    }
}
