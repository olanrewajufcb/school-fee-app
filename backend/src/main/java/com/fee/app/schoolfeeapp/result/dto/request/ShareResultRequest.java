package com.fee.app.schoolfeeapp.result.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShareResultRequest(
        @NotBlank(message = "Channel is required")
        @Pattern(regexp = "^(SMS|WHATSAPP|EMAIL)$", message = "Channel must be SMS, WHATSAPP, or EMAIL")
        String channel,

        @NotBlank(message = "Recipient is required")
        String recipient
) {}