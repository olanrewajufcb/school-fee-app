package com.fee.app.schoolfeeapp.student.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EnrollStudentRequest(
        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        String middleName,

        @NotBlank(message = "Gender is required")
        @Pattern(regexp = "^(MALE|FEMALE)$", message = "Gender must be MALE or FEMALE")
        String gender,

        LocalDate dateOfBirth,

        @NotNull(message = "Class ID is required")
        UUID classId,

        List<GuardianInfo> guardians,

        String medicalNotes
) {
    public record GuardianInfo(
            @NotBlank(message = "Guardian first name is required")
            String firstName,

            @NotBlank(message = "Guardian last name is required")
            String lastName,

            @NotBlank(message = "Guardian phone is required")
            @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
            String phone,

            String email,

            @NotBlank(message = "Relationship is required")
            String relationship,

            boolean isPrimaryContact,

            boolean canPickUpChild,
            boolean canViewFees,
            boolean canViewResults,
            boolean canViewAttendance,
            boolean canReceiveSms,

            int contactPriority
    ) {}
}