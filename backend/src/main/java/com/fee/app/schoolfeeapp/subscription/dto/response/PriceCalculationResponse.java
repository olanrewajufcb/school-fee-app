package com.fee.app.schoolfeeapp.subscription.dto.response;

import com.fee.app.schoolfeeapp.subscription.enums.BillingCycle;
import com.fee.app.schoolfeeapp.subscription.enums.PlanCode;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PriceCalculationResponse(
        PlanCode planCode,
        String planName,
        BillingCycle billingCycle,
        Integer studentCount,
        BigDecimal ratePerStudent,
        BigDecimal totalAmount,
        String currency,
        BigDecimal savingsAmount
) {}
