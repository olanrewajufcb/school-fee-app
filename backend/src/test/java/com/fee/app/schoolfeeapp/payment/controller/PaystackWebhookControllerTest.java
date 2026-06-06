package com.fee.app.schoolfeeapp.payment.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.service.PaymentService;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaystackWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    private PaystackWebhookController controller;

    private static final String SECRET = "sk_test_unit";

    @BeforeEach
    void setUp() {
        controller = new PaystackWebhookController(paymentService);
        ReflectionTestUtils.setField(controller, "paystackSecretKey", SECRET);
    }

    @Test
    @DisplayName("Should process callback with valid Paystack signature")
    void shouldProcessCallbackWithValidPaystackSignature() {
        String rawPayload = rawPayload();
        when(paymentService.handlePaystackWebhook(rawPayload)).thenReturn(Mono.empty());

        StepVerifier.create(controller.handleCallback(rawPayload, signature(rawPayload)))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Map<String, String>> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).containsEntry("message", "Webhook processed successfully");
                })
                .verifyComplete();

        verify(paymentService).handlePaystackWebhook(rawPayload);
    }

    @Test
    @DisplayName("Should reject callback with invalid Paystack signature")
    void shouldRejectCallbackWithInvalidPaystackSignature() {
        String rawPayload = rawPayload();

        StepVerifier.create(controller.handleCallback(rawPayload, "bad-signature"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isSuccess()).isFalse();
                })
                .verifyComplete();

        verify(paymentService, never()).handlePaystackWebhook(rawPayload);
    }

    @Test
    @DisplayName("Should reject callback with missing Paystack signature")
    void shouldRejectCallbackWithMissingPaystackSignature() {
        String rawPayload = rawPayload();

        StepVerifier.create(controller.handleCallback(rawPayload, null))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isSuccess()).isFalse();
                })
                .verifyComplete();

        verify(paymentService, never()).handlePaystackWebhook(rawPayload);
    }

    @Test
    @DisplayName("Should reject callback when Paystack secret is not configured")
    void shouldRejectCallbackWhenPaystackSecretIsNotConfigured() {
        String rawPayload = rawPayload();
        ReflectionTestUtils.setField(controller, "paystackSecretKey", "");

        StepVerifier.create(controller.handleCallback(rawPayload, signature(rawPayload)))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().isSuccess()).isFalse();
                })
                .verifyComplete();

        verify(paymentService, never()).handlePaystackWebhook(rawPayload);
    }

    @Test
    @DisplayName("Should propagate service errors after signature validation")
    void shouldPropagateServiceErrorsAfterSignatureValidation() {
        String rawPayload = rawPayload();
        SchoolFeeException expectedError = new SchoolFeeException(
                "PAYMENT_NOT_FOUND",
                "No pending payment found for: paystack-ref");
        when(paymentService.handlePaystackWebhook(rawPayload)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(controller.handleCallback(rawPayload, signature(rawPayload)))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(paymentService).handlePaystackWebhook(rawPayload);
    }

    private String rawPayload() {
        return """
                {"event":"charge.success","data":{"reference":"paystack-ref","status":"success","amount":500000}}
                """.trim();
    }

    private String signature(String rawPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
