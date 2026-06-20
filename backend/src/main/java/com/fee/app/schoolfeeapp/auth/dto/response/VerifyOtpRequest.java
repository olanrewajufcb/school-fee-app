package com.fee.app.schoolfeeapp.auth.dto.response;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record VerifyOtpRequest(
        @NotBlank(message = "Phone number is required")
        String phoneNumber,

        @NotBlank(message = "OTP code is required")
        String otpCode,
        UUID schoolId
) {}