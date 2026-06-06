package com.fee.app.schoolfeeapp.result.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ReportCommentResponse(
        UUID studentId,
        UUID termId,
        String comment,
        Instant updatedAt
) {}