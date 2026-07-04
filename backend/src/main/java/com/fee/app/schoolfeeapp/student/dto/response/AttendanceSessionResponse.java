package com.fee.app.schoolfeeapp.student.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record AttendanceSessionResponse(
        UUID sessionId,
        UUID classId,
        String className,
        UUID termId,
        String termName,
        LocalDate date,
        String sessionType,       // MORNING_ARRIVAL or AFTERNOON_DEPARTURE
        boolean isComplete,
        int totalStudents,
        int markedCount,
        int presentCount,
        int absentCount,
        int lateCount
) {}