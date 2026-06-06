package com.fee.app.schoolfeeapp.result.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PublishResultResponse(
        UUID termId,
        String status,
        Instant publishedAt,
        String publishedBy,
        String message
) {}