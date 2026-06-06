package com.fee.app.schoolfeeapp.school.dto.response;



import java.time.Instant;
import java.util.UUID;


public record SchoolSummaryResponse(
        UUID schoolId,
        String name,
        String code,
        String city,
        String state,
        int studentCount,
        int activeUsers,
        String status,
        String currentTerm,
        double collectionRate,
        Instant createdAt
) {}