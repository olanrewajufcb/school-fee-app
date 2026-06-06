package com.fee.app.schoolfeeapp.auth.dto.response;


import java.util.Set;
import java.util.UUID;

public record CreateStaffResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String userType,
        Set<String> roles,
        UUID schoolId,
        String schoolName,
        String temporaryPassword,
        String message
) {}