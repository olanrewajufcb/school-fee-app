package com.fee.app.schoolfeeapp.school.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateSchoolRequest(

        @NotBlank(message = "School name is required")
        @Size(min = 3, max = 255, message = "School name must be between 3 and 255 characters")
        String name,

        @NotBlank(message = "School code is required")
        @Pattern(regexp = "^[A-Z0-9]{3,10}$", message = "School code must be 3-10 uppercase alphanumeric characters")
        String code,

        @NotBlank(message = "Email is required")
        @Pattern(regexp = "^[\\w.-]+@[\\w.-]+\\.\\w{2,}$", message = "Invalid email format")
        String email,

        @NotBlank(message = "Phone is required")
        String phone,

        String address,
        String city,
        String state,
        String country,

        String logoUrl,

        PaymentConfig paymentConfig,
        SmsConfig smsConfig,
        TermConfig termConfig,

        @Valid
        AdminUser adminUser

) {
    public record PaymentConfig(
            String paystackPublicKey,
            String paystackSubaccountCode,
            List<String> acceptedPaymentMethods,
            String flutterwavePublicKey,
            String flutterwaveSecretKey
    ) {}

    public record SmsConfig(
            String provider,
            String apiKey,
            String username,
            String senderId,
            String defaultCountryCode
    ) {}

    public record TermConfig(
            int termsPerYear,
            List<String> termNames,
            String academicYearStart
    ) {}

    public record AdminUser(
            @NotBlank(message = "Admin email is required")
            @Pattern(regexp = "^[\\w.-]+@[\\w.-]+\\.\\w{2,}$", message = "Invalid email format")
            String email,

            @NotBlank(message = "Admin first name is required")
            String firstName,

            @NotBlank(message = "Admin last name is required")
            String lastName,

            @NotBlank(message = "Admin phone is required")
            String phoneNumber
    ) {}
}