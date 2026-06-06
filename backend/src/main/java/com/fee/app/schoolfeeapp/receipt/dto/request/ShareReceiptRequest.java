package com.fee.app.schoolfeeapp.receipt.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShareReceiptRequest(
        @NotBlank(message = "Channel is required")
        @Pattern(regexp = "^(SMS|EMAIL|WHATSAPP)$", message = "Channel must be SMS, EMAIL, or WHATSAPP")
        String channel,

        @NotBlank(message = "Recipient is required")
        String recipient
) {}