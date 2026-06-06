package com.fee.app.schoolfeeapp.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker interface for all domain events.
 *
 * Every event must have:
 * - aggregateId: The root entity this event belongs to
 * - eventType: Unique event type string for routing
 * - occurredAt: When the event happened
 */
public interface DomainEvent {

    UUID getAggregateId();
    String getEventType();
    Instant getOccurredAt();
}