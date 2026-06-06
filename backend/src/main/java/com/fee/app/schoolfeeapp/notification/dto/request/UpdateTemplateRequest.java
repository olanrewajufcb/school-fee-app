package com.fee.app.schoolfeeapp.notification.dto.request;

public record UpdateTemplateRequest(
        String body,
        String name,
        Boolean isActive
) {}