package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TraitAssessmentRequest(
        @NotNull UUID classId,
        @NotNull UUID termId,
        @NotEmpty @Valid List<Entry> entries
) {
    public record Entry(
            @NotNull UUID studentId,
            @NotNull UUID traitId,
            @NotNull @Size(max = 20) String rating,
            @Size(max = 500) String comment
    ) {}
}
