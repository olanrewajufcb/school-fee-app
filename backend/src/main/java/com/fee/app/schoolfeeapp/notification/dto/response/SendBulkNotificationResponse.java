package com.fee.app.schoolfeeapp.notification.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record SendBulkNotificationResponse(
        UUID batchId,
        int recipientsCount,
        BigDecimal estimatedCost,
        String status,
        String message
) {}