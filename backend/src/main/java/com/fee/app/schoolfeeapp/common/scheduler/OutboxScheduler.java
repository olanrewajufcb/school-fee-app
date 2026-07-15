package com.fee.app.schoolfeeapp.common.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process scheduling adapter for local development and deployments that do
 * not use a dedicated Cloud Run Job.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.scheduler.outbox-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxScheduler {

    private final OutboxEventProcessor processor;

    @Scheduled(fixedDelayString = "${app.scheduler.outbox-delay:5000}")
    public void processPendingEvents() {
        processor.processPendingEvents();
    }
}
