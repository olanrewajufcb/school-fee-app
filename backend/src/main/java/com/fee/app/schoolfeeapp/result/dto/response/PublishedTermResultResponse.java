package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record PublishedTermResultResponse(
        UUID termId,
        String termName,
        String sessionName,
        double average,
        String overallGrade,
        int classPosition,
        int outOf
) {}
