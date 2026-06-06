package com.fee.app.schoolfeeapp.student.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateStudentRequest(
        String firstName,
        String middleName,
        String lastName,
        String gender,
        LocalDate dateOfBirth,
        UUID currentClassId,
        String enrollmentStatus,
        String medicalNotes
) {}