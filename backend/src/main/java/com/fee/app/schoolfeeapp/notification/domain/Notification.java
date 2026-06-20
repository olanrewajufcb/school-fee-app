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

@Builder
@Data
@Table("notification.notifications")
public class Notification {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    @Column("recipient_id")
    private UUID recipientId;

    @Column("recipient_phone")
    private String recipientPhone;

    @Column("recipient_email")
    private String recipientEmail;

    private String channel;

    @Column("template_code")
    private String templateCode;

    private String subject;

    private String body;

    @Column("rendered_body")
    private String renderedBody;

    private String status;

    @Column("provider_id")
    private UUID providerId;

    @Column("provider_message_id")
    private String providerMessageId;

    @Column("provider_response")
    private JsonNode providerResponse;

    @Column("provider_cost")
    private BigDecimal providerCost;

    @Column("retry_count")
    private Integer retryCount;

    @Column("max_retries")
    private Integer maxRetries;

    @Column("next_retry_at")
    private Instant nextRetryAt;

    @Column("error_message")
    private String errorMessage;

    @Column("correlation_id")
    private UUID correlationId;

    @Column("context_type")
    private String contextType;

    @Column("context_id")
    private UUID contextId;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("created_at")
    private Instant createdAt;

    @Column("sent_at")
    private Instant sentAt;

    @Column("delivered_at")
    private Instant deliveredAt;

    }
