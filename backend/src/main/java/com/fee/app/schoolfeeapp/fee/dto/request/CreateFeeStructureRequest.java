package com.fee.app.schoolfeeapp.fee.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateFeeStructureRequest(
        @NotBlank(message = "Fee structure name is required")
        String name,

        @NotNull(message = "Academic session ID is required")
        UUID sessionId,

        @NotNull(message = "Term ID is required")
        UUID termId,

        @NotEmpty(message = "At least one class must be specified")
        List<UUID> applicableToClassIds,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @NotEmpty(message = "At least one fee item is required")
        @Valid
        List<FeeItemRequest> items,

        LateFeeConfig lateFeeConfig
) {
    public record FeeItemRequest(
            UUID categoryId,
            @NotBlank(message = "Description is required")
            String description,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be greater than 0")
            BigDecimal amount,

            boolean isMandatory,
            int sortOrder
    ) {}

    public record LateFeeConfig(
            int applyAfterDays,
            @Positive(message = "Percentage must be greater than 0")
            double percentageAmount,
            BigDecimal flatAmount
    ) {}
}