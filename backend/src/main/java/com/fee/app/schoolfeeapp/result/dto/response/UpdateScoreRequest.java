package com.fee.app.schoolfeeapp.result.dto.response;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateScoreRequest(
        @NotNull BigDecimal score,
        String reason
) {}