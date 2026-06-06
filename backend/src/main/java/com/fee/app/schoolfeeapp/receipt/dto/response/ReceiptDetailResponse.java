package com.fee.app.schoolfeeapp.receipt.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReceiptDetailResponse(
        String receiptNumber,
        UUID paymentId,
        String schoolName,
        String schoolAddress,
        String paidBy,
        BigDecimal amount,
        String amountInWords,
        String paymentMethod,
        Instant paymentDate,
        List<BreakdownItem> breakdown,
        Instant generatedAt,
        boolean smsSent,
        boolean emailSent
) {
    public record BreakdownItem(
            String studentName,
            String admissionNumber,
            String className,
            String term,
            BigDecimal amount
    ) {}
}