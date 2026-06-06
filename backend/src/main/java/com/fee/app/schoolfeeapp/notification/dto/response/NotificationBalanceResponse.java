package com.fee.app.schoolfeeapp.notification.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NotificationBalanceResponse(
        String provider,
        int balance,
        String currency,
        BigDecimal costPerSms,
        LocalDate lastPurchased,
        int estimatedRemainingDays
) {}