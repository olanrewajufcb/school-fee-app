package com.fee.app.schoolfeeapp.auth.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("auth.audit_log")
public class AuditLog {
    
    @Id
    private UUID id;
    
    @Column("user_id")
    private UUID userId;
    
    private String action;
    
    @Column("ip_address")
    private InetAddress ipAddress;
    
    @Column("user_agent")
    private String userAgent;
    
    private JsonNode metadata;
    
    @Column("created_at")
    private Instant createdAt;

    }