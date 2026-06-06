package com.fee.app.schoolfeeapp.payment.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class PaystackGatewayTest {

    private final PaystackGateway gateway = new PaystackGateway(new ObjectMapper());

    @Test
    @DisplayName("Should parse successful Paystack charge callback")
    void shouldParseSuccessfulPaystackChargeCallback() {
        String rawPayload = """
                {
                  "event": "charge.success",
                  "data": {
                    "id": 123456789,
                    "reference": "paystack-ref-123",
                    "status": "success",
                    "amount": 500000,
                    "gateway_response": "Approved",
                    "customer": {"phone": "08012345678"},
                    "metadata": {"payment_id": "payment-123"}
                  }
                }
                """;

        StepVerifier.create(gateway.handleCallback(rawPayload))
                .assertNext(callback -> {
                    assertThat(callback.isSuccess()).isTrue();
                    assertThat(callback.gatewayTransactionRef()).isEqualTo("paystack-ref-123");
                    assertThat(callback.gatewayReceiptNumber()).isEqualTo("123456789");
                    assertThat(callback.amount()).isEqualByComparingTo("5000");
                    assertThat(callback.phoneNumber()).isEqualTo("08012345678");
                    assertThat(callback.resultDescription()).isEqualTo("Approved");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should parse failed Paystack charge callback with reference")
    void shouldParseFailedPaystackChargeCallbackWithReference() {
        String rawPayload = """
                {
                  "event": "charge.failed",
                  "data": {
                    "id": 123456790,
                    "reference": "paystack-ref-456",
                    "status": "failed",
                    "amount": 250000,
                    "gateway_response": "Card declined",
                    "customer": {"phone": "08000000000"}
                  }
                }
                """;

        StepVerifier.create(gateway.handleCallback(rawPayload))
                .assertNext(callback -> {
                    assertThat(callback.isSuccess()).isFalse();
                    assertThat(callback.gatewayTransactionRef()).isEqualTo("paystack-ref-456");
                    assertThat(callback.gatewayReceiptNumber()).isEqualTo("123456790");
                    assertThat(callback.amount()).isEqualByComparingTo("2500");
                    assertThat(callback.resultDescription()).isEqualTo("Card declined");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should ignore unrelated Paystack event")
    void shouldIgnoreUnrelatedPaystackEvent() {
        String rawPayload = """
                {"event":"transfer.success","data":{"reference":"transfer-ref"}}
                """;

        StepVerifier.create(gateway.handleCallback(rawPayload))
                .assertNext(callback -> {
                    assertThat(callback.isSuccess()).isFalse();
                    assertThat(callback.gatewayTransactionRef()).isNull();
                    assertThat(callback.resultDescription()).isEqualTo("Event ignored: transfer.success");
                })
                .verifyComplete();
    }
}
