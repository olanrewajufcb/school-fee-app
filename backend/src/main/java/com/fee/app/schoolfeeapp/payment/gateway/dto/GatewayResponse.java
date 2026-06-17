package com.fee.app.schoolfeeapp.payment.gateway.dto;

import lombok.Builder;

/**
 * Standardized response from any payment gateway.
 * Each gateway implementation maps its specific response to this common format.
 */
@Builder
public record GatewayResponse(
        String gatewayTransactionRef,
        String status,
        String message,
        String authorizationUrl,
        String rawResponse,
        int expiresInSeconds
) {}