package com.fee.app.schoolfeeapp.auth.domain;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Builder
@Data
public class StudentGuardianLinkProjection {

    private UUID studentId;
    private UUID guardianId;
    private String firstName;
    private String lastName;
    private String className;
    private String admissionNumber;
    private String relationship;
    private Boolean canViewFees;
    private Boolean canViewResults;
    private Boolean canViewAttendance;
}
