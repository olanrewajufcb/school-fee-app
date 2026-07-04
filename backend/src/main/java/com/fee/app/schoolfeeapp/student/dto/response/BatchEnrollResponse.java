package com.fee.app.schoolfeeapp.student.dto.response;

import java.util.List;
import java.util.UUID;

public record BatchEnrollResponse(
        int totalSubmitted,
        int enrolled,
        int failed,
        List<EnrollmentResult> results
) {
    public record EnrollmentResult(
            String status,        // ENROLLED, FAILED
            UUID studentId,       // null if failed
            String admissionNumber, // null if failed
            String firstName,
            String lastName,
            String reason         // null if enrolled
    ) {}
}