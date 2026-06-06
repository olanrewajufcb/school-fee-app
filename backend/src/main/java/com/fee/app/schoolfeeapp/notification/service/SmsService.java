package com.fee.app.schoolfeeapp.notification.service;

import reactor.core.publisher.Mono;

public interface SmsService {
    
    /**
     * Send an SMS message.
     * 
     * @param phoneNumber Recipient phone number in normalized format (234XXXXXXXXXX)
     * @param message SMS body
     * @return Mono that completes when the SMS is sent (or queued)
     */
    Mono<Void> send(String phoneNumber, String message);
    
    /**
     * Get current SMS balance from provider.
     */
    Mono<Integer> getBalance();
}