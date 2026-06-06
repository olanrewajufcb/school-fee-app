package com.fee.app.schoolfeeapp.school.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PromoteStudentsRequest(
        @NotNull(message = "Source class ID is required")
        UUID fromClassId,

        @NotNull(message = "Target class ID is required")
        UUID toClassId,

        @NotEmpty(message = "At least one student must be selected")
        List<UUID> studentIds,

        @NotNull(message = "New session ID is required")
        UUID newSessionId
) {}