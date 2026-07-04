package com.fee.app.schoolfeeapp.result.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCardTemplateRequest(
        @NotBlank(message = "Template name is required")
        @Size(max = 100, message = "Template name cannot exceed 100 characters")
        String name,

        @NotBlank(message = "Education level is required")
        String educationLevel,

        @NotNull(message = "Template config is required")
        JsonNode config,

        boolean isDefault
) {}
