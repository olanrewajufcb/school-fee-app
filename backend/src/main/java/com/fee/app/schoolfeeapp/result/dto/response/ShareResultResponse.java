package com.fee.app.schoolfeeapp.result.dto.response;

import java.time.Instant;

public record ShareResultResponse(
        String channel,
        Instant sentAt,
        String message,
        String shareText,
        String shareUrl
) {}
