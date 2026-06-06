package com.fee.app.schoolfeeapp.notification.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table(name = "notification_templates", schema = "notification")
public class NotificationTemplate {
    
    @Id
    private UUID id;
    
    @Column("school_id")
    private UUID schoolId;
    
    @Column("template_code")
    private String templateCode;
    
    private String name;
    
    private String channel;
    
    private String subject;
    
    @Column("body_template")
    private String bodyTemplate;
    
    private JsonNode variables;
    
    @Column("is_default")
    private Boolean isDefault;

    @Column("is_active")
    private Boolean isActive;
    
    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("deleted_at")
    private Instant deletedAt;

    @Version
    private Integer version;
}
