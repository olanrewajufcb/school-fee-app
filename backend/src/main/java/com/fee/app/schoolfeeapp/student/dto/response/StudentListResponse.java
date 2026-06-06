package com.fee.app.schoolfeeapp.student.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record StudentListResponse(
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
        String parentPhone,
        String parentName,
        String profilePhotoUrl
) {
    public record CurrentClass(
            UUID classId,
            String name,
            String gradeLevel
    ) {}
}