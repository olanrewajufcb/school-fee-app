package com.fee.app.schoolfeeapp.school.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateAcademicSessionRequest(
        @NotBlank(message = "Session name is required")
        String name,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @NotEmpty(message = "At least one term is required")
        @Valid
        List<TermRequest> terms,

        boolean setAsCurrent
) {
    public record TermRequest(
            @NotBlank(message = "Term name is required")
            String name,

            int termNumber,

            @NotNull(message = "Term start date is required")
            LocalDate startDate,

            @NotNull(message = "Term end date is required")
            LocalDate endDate
    ) {}
}