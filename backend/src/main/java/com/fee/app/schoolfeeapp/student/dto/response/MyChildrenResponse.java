package com.fee.app.schoolfeeapp.student.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MyChildrenResponse(
        UUID studentId,
        String admissionNumber,
        String firstName,
        String lastName,
        String currentClass,
        String profilePhotoUrl,
        FeeStatus feeStatus
) {
    public record FeeStatus(
            String termName,
            BigDecimal totalFee,
            BigDecimal amountPaid,
            BigDecimal balance,
            String status,
            LocalDate dueDate
    ) {}
}