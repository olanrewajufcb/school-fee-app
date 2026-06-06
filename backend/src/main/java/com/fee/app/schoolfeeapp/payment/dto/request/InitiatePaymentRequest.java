package com.fee.app.schoolfeeapp.payment.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record InitiatePaymentRequest(
        @NotEmpty(message = "At least one fee must be selected")
        List<UUID> studentFeeIds,

        @NotNull(message = "Payment method is required")
        String paymentMethod,

        @NotNull(message = "Phone number is required")
        String phoneNumber,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        BigDecimal amount,

        Map<UUID, List<String>> payOptionalItems
) {}