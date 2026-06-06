package com.fee.app.schoolfeeapp.payment.gateway;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record GatewayCallbackData(
        String gatewayTransactionRef,
        String gatewayReceiptNumber,
        BigDecimal amount,
        String phoneNumber,
        boolean isSuccess,
        String resultDescription,
        String rawPayload
) {}
