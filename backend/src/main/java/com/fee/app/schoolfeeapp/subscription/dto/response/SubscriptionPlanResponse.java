package com.fee.app.schoolfeeapp.subscription.dto.response;

import com.fee.app.schoolfeeapp.subscription.enums.PlanCode;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record SubscriptionPlanResponse(
        UUID id,
        PlanCode code,
        String name,
        String description,
        BigDecimal pricePerStudentTermly,
        BigDecimal pricePerStudentAnnually,
        String currency,
        Boolean hasOnlinePayments,
        Boolean hasResultsManagement,
        Boolean hasAttendanceTracking,
        Boolean hasAutomatedNotifications
) {}
