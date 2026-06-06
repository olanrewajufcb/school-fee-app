package com.fee.app.schoolfeeapp.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID paymentId,
        String status,
        BigDecimal amount,
        String paymentMethod,
        String transactionReference,
        Instant paidAt,
        ReceiptInfo receipt
) {
    public record ReceiptInfo(
            String receiptNumber,
            String receiptUrl,
            List<BreakdownItem> breakdown
    ) {}

    public record BreakdownItem(
            String studentName,
            String admissionNumber,
            String feeDescription,
            BigDecimal amount
    ) {}
}