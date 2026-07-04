package com.fee.app.schoolfeeapp.student.dto.response;

import java.util.List;
import java.util.UUID;

public record TodayAttendanceResponse(
        UUID classId,
        String className,
        String date,
        int totalStudents,
        int present,
        int absent,
        int late,
        int notMarked,
        List<AttendanceResponse> students
) {}