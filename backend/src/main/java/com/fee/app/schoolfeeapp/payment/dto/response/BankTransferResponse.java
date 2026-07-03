package com.fee.app.schoolfeeapp.payment.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record BankTransferResponse(
        String reference,
        String accountNumber,
        String accountName,
        String bankName,
        BigDecimal amount,
        String expiresAt,
        String status,
        String message
) {}