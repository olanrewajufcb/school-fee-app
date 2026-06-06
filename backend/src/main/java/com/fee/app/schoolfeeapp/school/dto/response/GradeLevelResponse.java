package com.fee.app.schoolfeeapp.school.dto.response;

public record GradeLevelResponse(
        String code,
        String name,
        String category,
        int sortOrder
) {}