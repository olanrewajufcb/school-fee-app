package com.fee.app.schoolfeeapp.auth.dto.response;

import java.util.UUID;

public record GuardianInvitationResponse(
        UUID guardianId,
        String guardianName,
        String phoneNumber,
        String email,
        boolean invitationSent,
        String invitationToken,
        String message
) {
    public GuardianInvitationResponse(
            UUID guardianId,
            String guardianName,
            String phoneNumber,
            boolean invitationSent,
            String invitationToken,
            String message
    ) {
        this(guardianId, guardianName, phoneNumber, null, invitationSent, invitationToken, message);
    }
}