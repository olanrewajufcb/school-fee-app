package com.fee.app.schoolfeeapp.notification.channel;

import com.fee.app.schoolfeeapp.notification.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmsChannel implements NotificationChannel {

    private static final BigDecimal COST_PER_SMS = BigDecimal.valueOf(4.00);

    private final SmsService smsService;

    @Override
    public String getChannel() {
        return "SMS";
    }

    @Override
    public Mono<ChannelResult> send(String recipient, String message, String contextId) {
        return smsService.send(recipient, message)
                .thenReturn(ChannelResult.builder()
                        .channel(getChannel())
                        .success(true)
                        .messageId("SMS-" + contextId)
                        .build())
                .onErrorResume(error -> {
                    log.error("SMS send failed to {}: {}", recipient, error.getMessage());
                    return Mono.just(ChannelResult.builder()
                            .channel(getChannel())
                            .success(false)
                            .errorMessage(error.getMessage())
                            .build());
                });
    }

    @Override
    public BigDecimal getCostPerMessage() {
        return COST_PER_SMS;
    }

    @Override
    public Mono<Integer> getBalance() {
        return smsService.getBalance();
    }

    @Override
    public Mono<Boolean> isAvailable() {
        return smsService.getBalance()
                .map(balance -> balance >= 0)
                .onErrorReturn(false);
    }

    @Override
    public String getProviderName() {
        return "AFRICAS_TALKING";
    }
}
