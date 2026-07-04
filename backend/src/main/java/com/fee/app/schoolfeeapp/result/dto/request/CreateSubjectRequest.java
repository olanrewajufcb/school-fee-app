package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSubjectRequest(
        @NotBlank(message = "Subject name is required")
        @Size(max = 100, message = "Subject name must not exceed 100 characters")
        String name,

        @Size(max = 20, message = "Subject code must not exceed 20 characters")
        String code,

        @Size(max = 50, message = "Subject category must not exceed 50 characters")
        String category
) {}
