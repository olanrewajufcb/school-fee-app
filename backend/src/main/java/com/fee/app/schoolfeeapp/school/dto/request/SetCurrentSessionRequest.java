package com.fee.app.schoolfeeapp.school.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetCurrentSessionRequest(
        @NotNull(message = "Session ID is required")
        UUID sessionId
) {}