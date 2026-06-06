package com.fee.app.schoolfeeapp.payment.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@Data
@Table("payment.payment_gateway_configs")
public class PaymentGatewayConfig {
    
    @Id
    private UUID id;
    
    @Column("school_id")
    private UUID schoolId;
    
    @Column("gateway_name")
    private String gatewayName;
    
    @Column("is_active")
    private Boolean isActive;
    
    private JsonNode config;
    
    @Column("supported_channels")
    private List<String> supportedChannels;
    
    @Column("created_at")
    private Instant createdAt;

    }
