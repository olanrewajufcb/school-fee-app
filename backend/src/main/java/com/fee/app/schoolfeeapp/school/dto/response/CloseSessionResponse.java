package com.fee.app.schoolfeeapp.school.dto.response;


import java.time.Instant;
import java.util.UUID;

public record CloseSessionResponse(
        UUID sessionId,
        String name,
        String status,
        Instant closedAt,
        int termsCompleted,
        int studentsArchived,
        String message
) {}