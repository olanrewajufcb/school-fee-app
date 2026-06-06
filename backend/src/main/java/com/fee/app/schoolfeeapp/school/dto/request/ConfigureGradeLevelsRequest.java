package com.fee.app.schoolfeeapp.school.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ConfigureGradeLevelsRequest(
        @NotEmpty(message = "At least one grade level must be enabled")
        List<String> enabledLevels,

        NamingConvention namingConvention
) {
    public record NamingConvention(
            String nursery,
            String primary,
            String juniorSecondary,
            String seniorSecondary
    ) {}
}