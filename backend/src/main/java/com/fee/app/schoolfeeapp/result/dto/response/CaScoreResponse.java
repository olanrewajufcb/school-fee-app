package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record CaScoreResponse(
        UUID batchId,
        String className,
        String subjectName,
        String caComponent,
        int scoresEntered,
        String message
) {}