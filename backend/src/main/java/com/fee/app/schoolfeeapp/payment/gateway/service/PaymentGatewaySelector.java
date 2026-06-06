package com.fee.app.schoolfeeapp.payment.gateway.service;

import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Selects the appropriate payment gateway based on payment method.
 * 
 * Uses the Strategy pattern: each gateway registers itself by payment method,
 * and this selector routes to the correct implementation.
 */
@Slf4j
@Component
public class PaymentGatewaySelector {

    private final Map<String, PaymentGateway> gatewayMap;

    /**
     * Spring injects all PaymentGateway implementations automatically.
     * They are indexed by their getPaymentMethod() return value.
     */
    public PaymentGatewaySelector(List<PaymentGateway> gateways) {
        this.gatewayMap = gateways.stream()
                .collect(Collectors.toUnmodifiableMap(
                        PaymentGateway::getPaymentMethod,
                        gateway -> gateway));
        log.info("Registered payment gateways: {}", gatewayMap.keySet());

    }

    /**
     * Select the appropriate gateway for a payment method.
     *
     * @throws SchoolFeeException if no gateway is registered for the method
     */
    public PaymentGateway select(String paymentMethod) {
        PaymentGateway gateway = gatewayMap.get(paymentMethod.toUpperCase());
        if (gateway == null) {
            throw new SchoolFeeException(
                    "UNSUPPORTED_PAYMENT_METHOD",
                    "No gateway configured for payment method: " + paymentMethod);
        }
        return gateway;
    }

    /**
     * Check if a payment method is supported.
     */
    public boolean supports(String paymentMethod) {
        return gatewayMap.containsKey(paymentMethod.toUpperCase());
    }

    /**
     * Get all supported payment methods.
     */
    public List<String> getSupportedMethods() {
        return List.copyOf(gatewayMap.keySet());
    }
}