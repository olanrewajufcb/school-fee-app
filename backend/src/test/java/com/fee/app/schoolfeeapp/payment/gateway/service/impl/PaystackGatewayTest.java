package com.fee.app.schoolfeeapp.payment.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayCallbackData;
import com.fee.app.schoolfeeapp.payment.gateway.GatewayStatus;
import com.fee.app.schoolfeeapp.payment.gateway.dto.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaystackGateway Unit Tests")
class PaystackGatewayTest {

    private PaystackGateway gateway;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ExchangeFunction exchangeFunction;

    @Captor
    private ArgumentCaptor<ClientRequest> requestCaptor;

    @BeforeEach
    void setUp() {
        gateway = new PaystackGateway(objectMapper);

        // Inject configuration properties
        ReflectionTestUtils.setField(gateway, "secretKey", "sk_test_12345");
        ReflectionTestUtils.setField(gateway, "baseUrl", "https://api.paystack.co");
        ReflectionTestUtils.setField(gateway, "callbackUrl", "https://api.schoolfee.app/callback");

        // Inject mocked WebClient
        WebClient mockWebClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ReflectionTestUtils.setField(gateway, "webClient", mockWebClient);
    }

    private void mockExternalApiResponse(String jsonBody) {
        ClientResponse mockResponse = ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));
    }

    @Nested
    @DisplayName("initiatePayment Tests")
    class InitiatePaymentTests {

        @Test
        @DisplayName("Should successfully initiate payment")
        void shouldSuccessfullyInitiatePayment() {
            UUID paymentId = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("5000.00"); // 5000 NGN = 500,000 Kobo
            String responseBody = """
                    {
                      "status": true,
                      "message": "Authorization URL created",
                      "data": {
                        "authorization_url": "https://checkout.paystack.com/access_code",
                        "access_code": "access_code",
                        "reference": "ref-123"
                      }
                    }
                    """;

            mockExternalApiResponse(responseBody);

            Mono<GatewayResponse> result = gateway.initiatePayment(paymentId, "08012345678", amount, "Term 1 Fee");

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.gatewayTransactionRef()).isEqualTo("ref-123");
                        assertThat(response.status()).isEqualTo("PROCESSING");
                        assertThat(response.message()).contains("https://checkout.paystack.com/access_code");
                        assertThat(response.authorizationUrl()).isEqualTo("https://checkout.paystack.com/access_code");
                        assertThat(response.expiresInSeconds()).isEqualTo(3600);
                    })
                    .verifyComplete();

            verify(exchangeFunction).exchange(requestCaptor.capture());
            assertThat(requestCaptor.getValue().url().toString()).isEqualTo("https://api.paystack.co/transaction/initialize");
        }

        @Test
        @DisplayName("Should reject zero or negative amount")
        void shouldRejectZeroAmount() {
            // Arrange
            UUID paymentId = UUID.randomUUID();

            // Act & Assert using AssertJ for a synchronous exception
           assertThatThrownBy(() -> {
                        gateway.initiatePayment(paymentId, "080123", BigDecimal.ZERO, "Fee");
                    })
                    .isInstanceOf(SchoolFeeException.class)
                    .matches(ex -> ((SchoolFeeException) ex).getErrorCode().equals("INVALID_PAYMENT_AMOUNT"))
                    .hasMessage("Amount must be greater than 0");
        }

        @Test
        @DisplayName("Should handle Paystack initialization failure")
        void shouldHandlePaystackInitFailure() {
            String responseBody = """
                    {
                      "status": false,
                      "message": "Invalid split code"
                    }
                    """;

            mockExternalApiResponse(responseBody);

            StepVerifier.create(gateway.initiatePayment(UUID.randomUUID(), "080123", new BigDecimal("100"), "Fee"))
                    .expectErrorMatches(throwable -> throwable instanceof SchoolFeeException &&
                            throwable.getMessage().contains("Invalid split code"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("verifyPayment Tests")
    class VerifyPaymentTests {

        @Test
        @DisplayName("Should successfully verify payment")
        void shouldVerifyPaymentSuccessfully() {
            String reference = "ref-verify-123";
            String responseBody = """
                    {
                      "status": true,
                      "data": {
                        "id": 987654,
                        "status": "success",
                        "amount": 1500000,
                        "gateway_response": "Successful",
                        "paid_at": "2026-06-08T12:00:00.000Z",
                        "customer": {
                          "phone": "08012345678"
                        }
                      }
                    }
                    """;

            mockExternalApiResponse(responseBody);

            Mono<GatewayStatus> result = gateway.verifyPayment(reference);

            StepVerifier.create(result)
                    .assertNext(status -> {
                        assertThat(status.isSuccess()).isTrue();
                        assertThat(status.gatewayTransactionRef()).isEqualTo(reference);
                        assertThat(status.gatewayReceiptNumber()).isEqualTo("987654");
                        assertThat(status.amount()).isEqualByComparingTo("15000"); // 1,500,000 kobo / 100
                        assertThat(status.resultDescription()).isEqualTo("Successful");
                        assertThat(status.phoneNumber()).isEqualTo("08012345678");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("handleCallback Tests")
    class HandleCallbackTests {

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
                        assertThat(callback.amount()).isEqualByComparingTo("5000"); // Kobo to Naira
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

    @Nested
    @DisplayName("isAvailable Tests")
    class IsAvailableTests {

        @Test
        @DisplayName("Should return true when secret key is valid")
        void shouldReturnTrueWhenKeyValid() {
            StepVerifier.create(gateway.isAvailable(UUID.randomUUID()))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when secret key is default")
        void shouldReturnFalseWhenKeyIsDefault() {
            ReflectionTestUtils.setField(gateway, "secretKey", "sk_test_default");

            StepVerifier.create(gateway.isAvailable(UUID.randomUUID()))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when secret key is null")
        void shouldReturnFalseWhenKeyIsNull() {
            ReflectionTestUtils.setField(gateway, "secretKey", null);

            StepVerifier.create(gateway.isAvailable(UUID.randomUUID()))
                    .expectNext(false)
                    .verifyComplete();
        }
    }
}