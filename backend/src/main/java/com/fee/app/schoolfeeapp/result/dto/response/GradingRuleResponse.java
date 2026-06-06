package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record GradingRuleResponse(
        UUID schoolId,
        int gradesCount,
        String message
) {}