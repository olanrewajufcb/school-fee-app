package com.fee.app.schoolfeeapp.common.events;

import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class StaffCreatedEvent {
    private UUID userId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String userType;
    private Set<String> roles;
    private UUID schoolId;
    private String schoolName;
    private UUID assignedBy;
}
