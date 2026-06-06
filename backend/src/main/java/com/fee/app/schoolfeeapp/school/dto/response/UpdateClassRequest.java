package com.fee.app.schoolfeeapp.school.dto.response;

import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record UpdateClassRequest(
        String name,
        String gradeLevel,
        UUID classTeacherId,

        @Positive(message = "Capacity must be greater than 0")
        Integer capacity
) {}
