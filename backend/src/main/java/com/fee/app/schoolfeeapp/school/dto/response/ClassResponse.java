package com.fee.app.schoolfeeapp.school.dto.response;


import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ClassResponse(
        UUID classId,
        String name,
        String gradeLevel,
        String section,
        String sessionName,
        ClassTeacher classTeacher,
        int capacity,
        int currentEnrollment,
        int availableSpots,
        List<UUID> studentIds,
        String status,
        Instant createdAt
) {
    public record ClassTeacher(
            UUID userId,
            String name
    ) {}
}