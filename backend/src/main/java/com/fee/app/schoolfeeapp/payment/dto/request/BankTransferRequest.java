package com.fee.app.schoolfeeapp.payment.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BankTransferRequest(
        @NotEmpty(message = "At least one fee must be selected")
        List<UUID> studentFeeIds,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than 0")
        BigDecimal amount,

        @NotNull(message = "Email is required for bank transfer")
        String email,

        String customerName
) {}