package com.fee.app.schoolfeeapp.result.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record GradingRuleRequest(
        @NotNull JsonNode config
) {}