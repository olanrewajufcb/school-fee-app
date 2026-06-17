package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record ExamLookupResponse(
        UUID id,
        String name,
        int maxScore
) {}
