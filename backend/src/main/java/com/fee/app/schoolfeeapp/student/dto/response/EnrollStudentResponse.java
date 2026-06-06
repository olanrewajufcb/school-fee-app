package com.fee.app.schoolfeeapp.student.dto.response;

import java.util.List;
import java.util.UUID;

public record EnrollStudentResponse(
        UUID studentId,
        String admissionNumber,
        String firstName,
        String lastName,
        UUID classId,
        String className,
        boolean parentCreated,
        List<UUID> parentUserIds,
        String message
) {}