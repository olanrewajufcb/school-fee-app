package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecomputeRankingsRequest(
        @NotNull UUID termId,
        @NotNull UUID classId
) {}