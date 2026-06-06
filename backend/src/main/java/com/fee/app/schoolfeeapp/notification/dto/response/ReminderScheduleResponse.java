package com.fee.app.schoolfeeapp.notification.dto.response;

import java.time.LocalTime;
import java.util.UUID;

public record ReminderScheduleResponse(
        UUID scheduleId,
        String name,
        String triggerType,
        int daysOffset,
        LocalTime sendTime,
        String templateCode,
        boolean isActive
) {}