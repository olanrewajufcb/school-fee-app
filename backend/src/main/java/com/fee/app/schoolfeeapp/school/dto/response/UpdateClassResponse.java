package com.fee.app.schoolfeeapp.school.dto.response;


import java.time.Instant;
import java.util.UUID;

public record UpdateClassResponse(
        UUID classId,
        String name,
        String classTeacher,
        int capacity,
        Instant updatedAt
) {}