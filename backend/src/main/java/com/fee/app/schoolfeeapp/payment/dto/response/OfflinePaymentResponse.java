package com.fee.app.schoolfeeapp.payment.dto.response;

import java.util.UUID;

public record OfflinePaymentResponse(
        UUID paymentId,
        String status,
        String receiptNumber,
        String approvedBy
) {}