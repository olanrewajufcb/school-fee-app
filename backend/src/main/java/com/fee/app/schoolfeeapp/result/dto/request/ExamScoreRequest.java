package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ExamScoreRequest(
        @NotNull UUID examId,
        @NotNull UUID classId,
        @NotNull UUID subjectId,
        @NotNull UUID termId,
        int maxScore,
        @NotEmpty @Valid List<ScoreEntry> scores
) {
    public record ScoreEntry(
            @NotNull UUID studentId,
            @NotNull BigDecimal score
    ) {}
}