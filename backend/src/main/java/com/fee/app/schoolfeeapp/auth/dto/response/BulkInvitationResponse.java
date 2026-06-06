package com.fee.app.schoolfeeapp.auth.dto.response;


import java.util.List;
import java.util.UUID;

public record BulkInvitationResponse(
        int totalRequested,
        int invitationsSent,
        int invitationsFailed,
        List<InvitationResult> results
) {
    public record InvitationResult(
            UUID guardianId,
            String phoneNumber,
            boolean success,
            String message
    ) {}
}