package com.fee.app.schoolfeeapp.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("job")
@RequiredArgsConstructor
@Slf4j
public class OutboxJobRunner implements CommandLineRunner {

    private final OutboxEventProcessor processor;

    @Override
    public void run(String... args) {
        log.info("Outbox One-off Job Execution Started...");
        try {
            processor.processPendingEventsSync().block();
            log.info("Outbox One-off Job Completed Successfully. Exiting JVM.");
            System.exit(0);
        } catch (Exception e) {
            log.error("Fatal error during outbox job execution", e);
            System.exit(1);
        }
    }
}
