package com.fee.app.schoolfeeapp.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateStaffRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank(message = "Phone number is required")
        String phoneNumber,

        @NotBlank(message = "User type is required")
        String userType, // SCHOOL_ADMIN, ACCOUNTANT, TEACHER

        @NotEmpty(message = "At least one role is required")
        Set<String> roles
) {}