package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record AssessmentTraitResponse(
        UUID traitId,
        String name,
        String category,
        Integer sortOrder
) {}
