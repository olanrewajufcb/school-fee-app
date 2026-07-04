package com.fee.app.schoolfeeapp.result.dto.response;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ReportCardTemplateResponse(
        UUID templateId,
        String name,
        String educationLevel,
        JsonNode config,
        boolean isDefault,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}
