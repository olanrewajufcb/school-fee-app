package com.fee.app.schoolfeeapp.school.dto.response;


import java.time.Instant;
import java.util.UUID;

public record UpdateSessionResponse(
        UUID sessionId,
        String name,
        Instant updatedAt
) {}