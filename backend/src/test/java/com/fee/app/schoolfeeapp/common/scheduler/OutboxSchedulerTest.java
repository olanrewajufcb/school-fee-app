package com.fee.app.schoolfeeapp.common.scheduler;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxSchedulerTest {

    @Test
    void delegatesScheduledProcessingToProcessor() {
        OutboxEventProcessor processor = mock(OutboxEventProcessor.class);
        OutboxScheduler scheduler = new OutboxScheduler(processor);

        scheduler.processPendingEvents();

        verify(processor).processPendingEvents();
    }
}
