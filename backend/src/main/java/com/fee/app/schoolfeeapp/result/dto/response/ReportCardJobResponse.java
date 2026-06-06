package com.fee.app.schoolfeeapp.result.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReportCardJobResponse(
        UUID jobId,
        String status,
        int totalStudents,
        int completedStudents,
        int failedStudents,
        String downloadUrl,
        Instant completedAt,
        String message
) {}