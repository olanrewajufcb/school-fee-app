package com.fee.app.schoolfeeapp.auth.dto.response;


import java.util.UUID;

public record CreateParentResponse(
        UUID userId,
        UUID guardianId,
        String phoneNumber,
        String email,
        String firstName,
        String lastName,
        String userType,
        int childrenLinked,
        boolean invitationSent,
        String temporaryPassword,
        String message
) {}