package com.fee.app.schoolfeeapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendOtpRequest(
        @NotBlank(message = "Phone number is required")
        String phoneNumber
) {}