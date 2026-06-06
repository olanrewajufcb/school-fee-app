package com.fee.app.schoolfeeapp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.List;
import java.util.UUID;

public record CreateParentRequest(
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid phone number")
        String phoneNumber,

        String email,

        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotEmpty(message = "At least one child must be linked")
        List<ChildLink> children
) {
    public record ChildLink(
            @NotBlank UUID studentId,
            @NotBlank String relationship,
            boolean isPrimaryContact
    ) {}
}