package com.fee.app.schoolfeeapp.result.dto.response;

public record CaConfigResponse(
        int componentCount,
        Double examWeightPercentage,
        String message
) {}