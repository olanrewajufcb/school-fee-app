package com.fee.app.schoolfeeapp.notification.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table(name = "reminder_schedules",schema = "notification")
public class ReminderSchedule {
    
    @Id
    private UUID id;
    
    @Column("school_id")
    private UUID schoolId;
    
    private String name;
    private String description;
    
    @Column("trigger_type")
    private String triggerType;
    
    @Column("template_code")
    private String templateCode;
    
    @Column("days_offset")
    private Integer daysOffset;
    
    @Column("send_time")
    private LocalTime sendTime;
    
    @Column("recipient_role")
    private String recipientRole;
    
    @Column("is_recurring")
    private Boolean isRecurring;
    
    @Column("recurring_interval_days")
    private Integer recurringIntervalDays;
    
    @Column("is_active")
    private Boolean isActive;
    
    @Column("created_at")
    private Instant createdAt;

    }