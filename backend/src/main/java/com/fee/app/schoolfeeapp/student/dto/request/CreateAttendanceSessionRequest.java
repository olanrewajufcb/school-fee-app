package com.fee.app.schoolfeeapp.student.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAttendanceSessionRequest(
        @NotNull(message = "Class ID is required")
        UUID classId,

        @NotNull(message = "Term ID is required")
        UUID termId,

        @NotNull(message = "Date is required")
        LocalDate date,

        @NotNull(message = "Session type is required")
        String sessionType  // MORNING_ARRIVAL or AFTERNOON_DEPARTURE
) {}