package com.fee.app.schoolfeeapp.school.events;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fee.app.schoolfeeapp.common.events.DomainEvent;

import java.time.Instant;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SchoolCreatedEvent(
        UUID schoolId,
        String schoolName,
        String schoolCode,
        UUID adminKeycloakId
) implements DomainEvent {

    @Override
    public UUID getAggregateId() {
        return schoolId;
    }

    @Override
    public String getEventType() {
        return "SCHOOL_CREATED";
    }

    @Override
    public Instant getOccurredAt() {
        return Instant.now();
    }
}
