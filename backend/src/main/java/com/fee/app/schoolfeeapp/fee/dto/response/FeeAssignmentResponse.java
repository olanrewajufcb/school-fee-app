package com.fee.app.schoolfeeapp.fee.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FeeAssignmentResponse(
        UUID structureId,
        int studentsAssigned,
        BigDecimal totalExpectedAmount,
        String status,
        LocalDate nextReminderDate,
        String message
) {}