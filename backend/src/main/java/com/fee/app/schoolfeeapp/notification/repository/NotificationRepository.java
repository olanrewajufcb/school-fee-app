package com.fee.app.schoolfeeapp.notification.repository;

import com.fee.app.schoolfeeapp.notification.domain.Notification;
import org.springframework.data.repository.query.Param;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends ReactiveCrudRepository<Notification, UUID> {

    @Query("""
            SELECT *
            FROM notification.notifications
            WHERE school_id = :schoolId
              AND context_type = :triggerType
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            """)
    Flux<Notification> findBySchoolIdAndTriggerTypeOrderByCreatedAtDesc(UUID schoolId, String triggerType);

    @Query("""
            SELECT *
            FROM notification.notifications
            WHERE context_type = :contextType
              AND context_id = :contextId
              AND deleted_at IS NULL
            ORDER BY created_at DESC
            """)
    Flux<Notification> findByContextTypeAndContextId(String contextType, UUID contextId);

    @Query("""
            INSERT INTO notification.notifications (
                id, school_id, recipient_id, recipient_phone, recipient_email, channel,
                template_code, subject, body, rendered_body, status,
                provider_message_id, provider_response, provider_cost, retry_count,
                max_retries, next_retry_at, error_message, correlation_id, context_type,
                context_id, idempotency_key, created_at, sent_at, delivered_at
            )
            VALUES (
                :#{#notification.id}, :#{#notification.schoolId}, :#{#notification.recipientId},
                :#{#notification.recipientPhone}, :#{#notification.recipientEmail},
                :#{#notification.channel}, :#{#notification.templateCode}, :#{#notification.subject},
                :#{#notification.body}, :#{#notification.renderedBody}, :#{#notification.status},
                :#{#notification.providerMessageId}, :#{#notification.providerResponse},
                :#{#notification.providerCost}, :#{#notification.retryCount},
                :#{#notification.maxRetries}, :#{#notification.nextRetryAt},
                :#{#notification.errorMessage}, :#{#notification.correlationId},
                :#{#notification.contextType}, :#{#notification.contextId},
                :#{#notification.idempotencyKey}, :#{#notification.createdAt},
                :#{#notification.sentAt}, :#{#notification.deliveredAt}
            )
            RETURNING *
            """)
    Mono<Notification> insertNotification(@Param("notification") Notification notification);

    @Query("""
            UPDATE notification.notifications
            SET status = :status,
                provider_message_id = :providerMessageId,
                provider_cost = :providerCost,
                error_message = :errorMessage,
                sent_at = :sentAt
            WHERE id = :id
            RETURNING *
            """)
    Mono<Notification> updateDeliveryResult(
            UUID id,
            String status,
            String providerMessageId,
            BigDecimal providerCost,
            String errorMessage,
            Instant sentAt);
}
