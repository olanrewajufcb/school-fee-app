package com.fee.app.schoolfeeapp.payment.gateway;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record GatewayStatus(
        String gatewayTransactionRef,
        String gatewayReceiptNumber,
        BigDecimal amount,
        String phoneNumber,
        boolean isSuccess,
        String resultDescription,
        Instant transactionDate
) {}
