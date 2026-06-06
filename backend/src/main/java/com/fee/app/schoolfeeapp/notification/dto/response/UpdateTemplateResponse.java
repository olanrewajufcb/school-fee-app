package com.fee.app.schoolfeeapp.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UpdateTemplateResponse(
        UUID templateId,
        Instant updatedAt
) {}