package com.fee.app.schoolfeeapp.auth.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {
    private UUID userId;
    private UUID keycloakId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String userType;
    private UUID schoolId;
    private String schoolName;
    private Set<String> roles;

    // Children are now fetched from database, not JWT
    private List<ChildInfo> children;

    private ZonedDateTime lastLogin;
    private boolean isActive;

    @Data
    @Builder
    public static class ChildInfo {
        private UUID studentId;
        private UUID guardianId;
        private String relationship;
        private boolean canViewFees;
        private boolean canViewResults;
        private boolean canViewAttendance;
    }
}