package com.fee.app.schoolfeeapp.subscription.dto.request;

import com.fee.app.schoolfeeapp.subscription.enums.BillingCycle;
import com.fee.app.schoolfeeapp.subscription.enums.PlanCode;
import jakarta.validation.constraints.NotNull;

public record UpgradeSubscriptionRequest(
        @NotNull PlanCode planCode,
        @NotNull BillingCycle billingCycle
) {}
