package com.fee.app.schoolfeeapp.school.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CreateSchoolResponse(
        UUID schoolId,
        String name,
        String code,
        String status,
        boolean adminUserCreated,
        String adminTemporaryPassword,
        UUID currentSessionId,
        String currentSessionName,
        Instant createdAt,
        String message
) {}