package com.fee.app.schoolfeeapp.school.dto.request;

import java.util.List;

public record UpdateSchoolRequest(
        String email,
        String phone,
        String address,
        String city,
        String state,
        String logoUrl,
        PaymentConfig paymentConfig,
        SmsConfig smsConfig
) {
    public record PaymentConfig(
            String paystackPublicKey,
            String paystackSubaccountCode,
            List<String> acceptedPaymentMethods
    ) {}

    public record SmsConfig(
            String provider,
            String apiKey,
            String username,
            String senderId
    ) {}
}