package com.fee.app.schoolfeeapp.fee.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CreateFeeStructureResponse(
        UUID structureId,
        String name,
        BigDecimal totalAmount,
        BigDecimal mandatoryAmount,
        int applicableClassCount,
        int estimatedStudentCount,
        LocalDate dueDate,
        String status,
        Instant createdAt
) {}