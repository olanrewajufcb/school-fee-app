package com.fee.app.schoolfeeapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;


public record LoginRequest(
        @NotBlank(message = "Username is required")
        String username,

        @NotBlank(message = "Password is required")
        String password,

        @NotBlank(message = "Client ID is required")
        String clientId
) {}
