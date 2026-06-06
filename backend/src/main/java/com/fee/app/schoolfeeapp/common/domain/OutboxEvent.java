package com.fee.app.schoolfeeapp.common.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Table(name = "outbox_events", schema = "outbox")
public class OutboxEvent {
    
    @Id
    private UUID id;
    
    @Column("event_type")
    private String eventType; // PARENT_INVITATION, STAFF_CREATED, etc.
    
    @Column("aggregate_id")
    private UUID aggregateId; // entityId being processed
    
    @Column("aggregate_type")
    private String aggregateType; // Type of aggregate (e.g., "PARENT", "STAFF")
    
    @Column("payload")
    private JsonNode payload; // JSON payload for processing
    
    @Column("status")
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    
    @Column("retry_count")
    private Integer retryCount;
    
    @Column("max_retries")
    private Integer maxRetries;
    
    @Column("next_retry_at")
    private Instant nextRetryAt;
    
    @Column("error_message")
    private String errorMessage;
    
    @Column("created_at")
    private Instant createdAt;
    
    @Column("processed_at")
    private Instant processedAt;
}
