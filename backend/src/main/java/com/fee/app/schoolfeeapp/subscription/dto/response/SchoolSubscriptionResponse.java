package com.fee.app.schoolfeeapp.subscription.dto.response;

import com.fee.app.schoolfeeapp.subscription.enums.BillingCycle;
import com.fee.app.schoolfeeapp.subscription.enums.SubscriptionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record SchoolSubscriptionResponse(
        UUID id,
        UUID schoolId,
        SubscriptionPlanResponse plan,
        BillingCycle billingCycle,
        SubscriptionStatus status,
        Integer studentCount,
        Instant trialStartDate,
        Instant trialEndDate,
        Long daysRemainingInTrial,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant gracePeriodEndDate,
        BigDecimal amountDue,
        Boolean autoRenew
) {}
