package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record CaConfigRequest(
        @NotEmpty(message = "At least one CA component is required")
        @Valid
        List<CaComponentRequest> components,

        @Positive(message = "Exam weight must be positive")
        double examWeightPercentage
) {
    public record CaComponentRequest(
            String name,
            int maxScore,
            double weightPercentage,
            int sortOrder
    ) {}
}