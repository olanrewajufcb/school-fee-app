package com.fee.app.schoolfeeapp.fee.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record StudentFeeResponse(
        UUID studentFeeId,
        String structureName,
        String termName,
        boolean isCurrentTerm,
        boolean isUpcomingTerm,
        List<FeeItemDetail> items,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal amountPaid,
        BigDecimal balance,
        LocalDate dueDate,
        long daysUntilDue,
        String status,
        boolean lateFeeApplicable,
        BigDecimal lateFeeAmount,
        ZonedDateTime lastReminderSent
) {
    public record FeeItemDetail(
            String description,
            BigDecimal amount,
            boolean isMandatory
    ) {}
}