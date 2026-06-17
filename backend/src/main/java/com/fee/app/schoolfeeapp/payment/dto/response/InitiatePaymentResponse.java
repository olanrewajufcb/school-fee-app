package com.fee.app.schoolfeeapp.payment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiatePaymentResponse(
        UUID paymentId,
        String status,
        String paymentMethod,
        BigDecimal amount,
        String gatewayMessage,
        String authorizationUrl,
        String checkoutRequestId,
        int expiresInSeconds
) {}