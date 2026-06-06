 package com.fee.app.schoolfeeapp.student.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UpdateStudentResponse(
        UUID studentId,
        String admissionNumber,
        String firstName,
        String lastName,
        UUID currentClassId,
        String className,
        String enrollmentStatus,
        Instant updatedAt
) {}