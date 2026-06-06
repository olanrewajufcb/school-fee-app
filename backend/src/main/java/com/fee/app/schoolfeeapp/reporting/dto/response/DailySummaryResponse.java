package com.fee.app.schoolfeeapp.reporting.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DailySummaryResponse(
        PeriodInfo period,
        BigDecimal totalCollected,
        int totalTransactions,
        Map<String, PaymentMethodSummary> byPaymentMethod,
        List<DailyBreakdown> dailyBreakdown
) {
    public record PeriodInfo(
            LocalDate startDate,
            LocalDate endDate
    ) {}

    public record PaymentMethodSummary(
            BigDecimal amount,
            int count
    ) {}

    public record DailyBreakdown(
            LocalDate date,
            BigDecimal amount,
            int transactions
    ) {}
}