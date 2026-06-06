package com.fee.app.schoolfeeapp.result.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record ExamScoreResponse(
        UUID batchId,
        String className,
        String subjectName,
        int scoresEntered,
        int finalScoresComputed,
        BigDecimal classAverage,
        BigDecimal classHighest,
        BigDecimal classLowest,
        String message
) {}