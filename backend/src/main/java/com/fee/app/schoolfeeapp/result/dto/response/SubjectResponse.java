package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record SubjectResponse(
        UUID subjectId,
        String name,
        String code,
        String category,
        boolean isActive
) {}