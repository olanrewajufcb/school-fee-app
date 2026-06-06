package com.fee.app.schoolfeeapp.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentHistoryResponse(
        UUID paymentId,
        Instant date,
        BigDecimal amount,
        String paymentMethod,
        String status,
        String description,
        String receiptNumber
) {}