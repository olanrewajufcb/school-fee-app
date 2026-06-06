package com.fee.app.schoolfeeapp.school.dto.response;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClassDetailResponse(
        UUID classId,
        String name,
        String gradeLevel,
        String section,
        String sessionName,
        ClassTeacher classTeacher,
        int capacity,
        int currentEnrollment,
        List<StudentSummary> students,
        ClassStatistics statistics,
        Instant createdAt
) {
    public record ClassTeacher(
            UUID userId,
            String name,
            String phoneNumber,
            String email
    ) {}

    public record StudentSummary(
            UUID studentId,
            String admissionNumber,
            String firstName,
            String lastName,
            String gender,
            String parentPhone,
            FeeStatus feeStatus
    ) {}

    public record FeeStatus(
            String termName,
            String status,
            BigDecimal balance
    ) {}

    public record ClassStatistics(
            int maleCount,
            int femaleCount,
            int fullyPaidFees,
            int pendingFees
    ) {}
}