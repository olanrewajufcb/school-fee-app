package com.fee.app.schoolfeeapp.school.dto.response;

import java.util.List;

public record ConfigureGradeLevelsResponse(
        int enabledLevels,
        List<String> enabledLevelCodes,
        String message
) {}