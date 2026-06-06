package com.fee.app.schoolfeeapp.auth.dto.response;


import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

public record UserSummaryResponse(
        UUID userId,
        String email,
        String phoneNumber,
        String firstName,
        String lastName,
        String userType,
        Set<String> roles,
        boolean isActive,
        int childrenCount,
        ZonedDateTime lastLogin,
        Instant createdAt
) {}