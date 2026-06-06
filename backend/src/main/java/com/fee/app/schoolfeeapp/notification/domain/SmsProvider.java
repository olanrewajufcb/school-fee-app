package com.fee.app.schoolfeeapp.notification.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("notification.sms_providers")
public class SmsProvider {
    
    @Id
    private UUID id;
    
    private String name;
    
    private JsonNode config;
    
    @Column("is_active")
    private Boolean isActive;
    
    private Integer priority;
    
    @Column("cost_per_sms")
    private BigDecimal costPerSms;
    
    @Column("created_at")
    private Instant createdAt;

    }