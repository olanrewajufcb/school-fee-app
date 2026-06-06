package com.fee.app.schoolfeeapp.common.repository;

import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEvent, UUID> {

    /**
     * Atomically claim pending events using FOR UPDATE SKIP LOCKED.
     *
     * This prevents race conditions in multi-pod deployments:
     * - FOR UPDATE: Locks selected rows
     * - SKIP LOCKED: Skips rows already locked by other transactions
     * - Single atomic operation: SELECT + UPDATE in one statement
     *
     * Only ONE pod will successfully claim each event.
     */
    @Query("""
        WITH claimed AS (
            SELECT id
            FROM outbox.outbox_events
            WHERE status = 'PENDING'
              AND next_retry_at <= :now
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
        )
        UPDATE outbox.outbox_events
        SET status = 'PROCESSING'
        FROM claimed
        WHERE outbox.outbox_events.id = claimed.id
        RETURNING outbox.outbox_events.*
        """)
    Flux<OutboxEvent> claimPendingEvents(Instant now, int limit);

    /**
     * Mark event as completed.
     * No race condition risk since we're the only ones who set it to PROCESSING.
     */
    @Query("""
        UPDATE outbox.outbox_events 
        SET status = 'COMPLETED', 
            processed_at = :processedAt
        WHERE id = :id AND status = 'PROCESSING'
        """)
    Mono<Void> markAsCompleted(UUID id, Instant processedAt);

    /**
     * Mark event as failed or reschedule for retry.
     * Only succeeds if we still own the lock (status = PROCESSING).
     */
    @Query("""
        UPDATE outbox.outbox_events 
        SET status = :status,
            retry_count = :retryCount,
            next_retry_at = :nextRetryAt,
            error_message = :errorMessage
        WHERE id = :id AND status = 'PROCESSING'
        """)
    Mono<Void> markAsFailed(
            UUID id,
            String status,
            int retryCount,
            Instant nextRetryAt,
            String errorMessage
    );

}
