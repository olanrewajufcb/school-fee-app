package com.fee.app.schoolfeeapp.student.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record StudentDetailResponse(
        UUID studentId,
        String admissionNumber,
        String firstName,
        String lastName,
        String middleName,
        String gender,
        LocalDate dateOfBirth,
        CurrentClass currentClass,
        LocalDate enrollmentDate,
        String status,
        List<ParentInfo> parents,
        FeeSummary currentTermFeeSummary,
        FeeSummary upcomingTermFeeSummary,
        String medicalNotes,
        String profilePhotoUrl
) {
    public record CurrentClass(
            UUID classId,
            String name,
            String gradeLevel,
            String classTeacher
    ) {}

    public record ParentInfo(
            UUID userId,
            UUID guardianId,
            String name,
            String phoneNumber,
            String relationship,
            boolean isPrimaryContact
    ) {}

    public record FeeSummary(
            String termName,
            BigDecimal totalFee,
            BigDecimal amountPaid,
            BigDecimal balance,
            String status,
            LocalDate dueDate
    ) {}
}