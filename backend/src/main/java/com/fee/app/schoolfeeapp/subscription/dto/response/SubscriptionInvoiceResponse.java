package com.fee.app.schoolfeeapp.subscription.dto.response;

import com.fee.app.schoolfeeapp.subscription.enums.BillingCycle;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder
public record SubscriptionInvoiceResponse(
        UUID id,
        UUID subscriptionId,
        UUID schoolId,
        String invoiceNumber,
        BigDecimal amount,
        String currency,
        Integer studentCount,
        BillingCycle billingCycle,
        String status,
        LocalDate dueDate,
        Instant paidAt,
        String paymentReference,
        Instant createdAt
) {}
