package com.fee.app.schoolfeeapp.school.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Builder
public record SchoolResponse(
        UUID schoolId,
        String name,
        String code,
        String email,
        String phone,
        String address,
        String city,
        String state,
        String country,
        String logoUrl,
        String status,
        CurrentTerm currentTerm,
        Map<String, Object> paymentConfig,
        Instant createdAt
) {
    @Builder
    public record CurrentTerm(
            UUID termId,
            String name,
            String sessionName,
            String startDate,
            String endDate
    ) {}
}