package com.fee.app.schoolfeeapp.payment.gateway.service;


import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus;
import com.fee.app.schoolfeeapp.payment.gateway.dto.GatewayResponse;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Strategy interface for payment gateway implementations.
 * 
 * Each payment method (for example Paystack or Flutterwave) implements this interface.
 * New gateways can be added without modifying existing code.
 */
public interface PaymentGateway {

    /**
     * The payment method this gateway handles.
     * Used as a key to select the correct strategy.
     */
    String getPaymentMethod();

    /**
     * Initiate a payment with this gateway.
     *
     * @param paymentId The payment record ID (used as account reference)
     * @param phoneNumber Customer's phone number
     * @param amount Amount to charge
     * @param narration Payment description
     * @return Response with gateway-specific details
     */
    Mono<GatewayResponse> initiatePayment(
            UUID paymentId, String phoneNumber, BigDecimal amount, String narration);

    /**
     * Verify the status of a payment with this gateway.
     *
     * @param gatewayTransactionRef The gateway's transaction reference
     * @return Current status from the gateway
     */
    Mono<GatewayStatus> verifyPayment(String gatewayTransactionRef);

    /**
     * Handle a callback/webhook from the gateway.
     *
     * @param rawPayload The raw callback body
     * @return Parsed callback data
     */
    Mono<GatewayCallbackData> handleCallback(String rawPayload);

    /**
     * Check if this gateway is configured and active for a given school.
     */
    Mono<Boolean> isAvailable(UUID schoolId);
}
