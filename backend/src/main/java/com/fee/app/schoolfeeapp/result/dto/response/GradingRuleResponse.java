package com.fee.app.schoolfeeapp.result.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record GradingRuleResponse(
        UUID schoolId,
        int gradesCount,
        String message,
        JsonNode config
) {}