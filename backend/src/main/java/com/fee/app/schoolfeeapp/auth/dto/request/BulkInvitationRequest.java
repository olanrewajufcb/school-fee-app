package com.fee.app.schoolfeeapp.auth.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkInvitationRequest(
        @NotEmpty(message = "At least one guardian ID is required")
        List<UUID> guardianIds
) {}