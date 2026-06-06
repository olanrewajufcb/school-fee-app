package com.fee.app.schoolfeeapp.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record SendBulkNotificationRequest(
        @NotEmpty(message = "At least one fee ID is required")
        List<UUID> studentFeeIds,

        @NotBlank(message = "Template code is required")
        String templateCode,

        @NotBlank(message = "Channel is required")
        @Pattern(regexp = "^(SMS|WHATSAPP|BOTH)$", message = "Channel must be SMS, WHATSAPP, or BOTH")
        String channel
) {}