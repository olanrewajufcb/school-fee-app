package com.fee.app.schoolfeeapp.school.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateClassRequest(
        @NotBlank(message = "Class name is required")
        String name,

        @NotBlank(message = "Grade level is required")
        String gradeLevel,

        String section,

        @NotNull(message = "Academic session ID is required")
        UUID academicSessionId,

        UUID classTeacherId,

        @Positive(message = "Capacity must be greater than 0")
        int capacity
) {}