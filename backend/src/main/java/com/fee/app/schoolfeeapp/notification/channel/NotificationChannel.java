package com.fee.app.schoolfeeapp.notification.channel;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Strategy interface for notification delivery channels.
 * Each channel (SMS, WhatsApp, Email) implements this interface.
 */
public interface NotificationChannel {

    /**
     * The channel name used for routing.
     */
    String getChannel();

    /**
     * Send a single notification.
     *
     * @param recipient Phone number or identifier
     * @param message Rendered message body
     * @param contextId Related entity ID (studentFeeId, paymentId, etc.)
     * @return Mono that completes when the message is delivered or queued
     */
    Mono<ChannelResult> send(String recipient, String message, String contextId);

    /**
     * Get the cost per message for this channel.
     */
    BigDecimal getCostPerMessage();

    /**
     * Get the current balance/credits for this channel.
     */
    Mono<Integer> getBalance();

    /**
     * Check if this channel is configured and available.
     */
    Mono<Boolean> isAvailable();

    /**
     * Get the provider name.
     */
    String getProviderName();
}