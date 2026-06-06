package com.fee.app.schoolfeeapp.fee.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record FeeStructureResponse(
        UUID structureId,
        String name,
        String termName,
        String sessionName,
        BigDecimal totalAmount,
        BigDecimal mandatoryAmount,
        List<String> applicableToClasses,
        int applicableClassCount,
        int studentCount,
        double collectionRate,
        LocalDate dueDate,
        String status,
        String createdBy,
        Instant createdAt
) {}