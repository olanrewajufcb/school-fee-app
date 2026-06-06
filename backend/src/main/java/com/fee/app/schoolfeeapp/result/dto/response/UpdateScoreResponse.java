package com.fee.app.schoolfeeapp.result.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UpdateScoreResponse(
        UUID scoreId,
        BigDecimal newScore,
        BigDecimal previousScore,
        Instant updatedAt
) {}