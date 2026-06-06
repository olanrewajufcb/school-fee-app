package com.fee.app.schoolfeeapp.notification.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationTemplateResponse(
        UUID templateId,
        String code,
        String name,
        String channel,
        String body,
        List<String> variables,
        boolean isDefault,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}