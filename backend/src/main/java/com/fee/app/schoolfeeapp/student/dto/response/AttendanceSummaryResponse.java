package com.fee.app.schoolfeeapp.student.dto.response;

public record AttendanceSummaryResponse(
        int totalSchoolDays,
        int daysPresent,
        int daysAbsent,
        int daysLate,
        int earlyPickups,
        double attendancePercentage
) {}