package com.fee.app.schoolfeeapp.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfflinePaymentRequest(
        @NotNull(message = "Student fee ID is required")
        UUID studentFeeId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotBlank(message = "Payment method is required")
        String paymentMethod,

        @NotNull(message = "Payment date is required")
        Instant paymentDate,

        String receivedBy,
        String notes,
        boolean generateReceipt
) {}