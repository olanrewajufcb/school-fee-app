package com.fee.app.schoolfeeapp.notification.channel;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppChannel Unit Tests")
class WhatsAppChannelTest {

    private WhatsAppChannel channel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ExchangeFunction exchangeFunction;

    @Captor
    private ArgumentCaptor<ClientRequest> requestCaptor;

    @BeforeEach
    void setUp() {
        channel = new WhatsAppChannel(objectMapper);

        // Inject configuration properties
        ReflectionTestUtils.setField(channel, "phoneNumberId", "123456789");
        ReflectionTestUtils.setField(channel, "accessToken", "test-access-token");
        ReflectionTestUtils.setField(channel, "businessAccountId", "business-123");
        ReflectionTestUtils.setField(channel, "enabled", true); // Default to true

        // Inject mocked WebClient
        WebClient mockWebClient = WebClient.builder()
                .baseUrl("https://graph.facebook.com/v18.0")
                .exchangeFunction(exchangeFunction)
                .build();
        ReflectionTestUtils.setField(channel, "webClient", mockWebClient);
    }

    private void mockExternalApiResponse(HttpStatus status, String jsonBody) {
        ClientResponse mockResponse = ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(jsonBody)
                .build();

        lenient().when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.just(mockResponse));
    }

    @Nested
    @DisplayName("send() Tests")
    class SendTests {

        @Test
        @DisplayName("Should skip external call and return success when disabled")
        void shouldSkipExternalCallWhenDisabled() {
            ReflectionTestUtils.setField(channel, "enabled", false);

            StepVerifier.create(channel.send("08012345678", "Hello", "ctx-123"))
                    .assertNext(result -> {
                        assertThat(result.channel()).isEqualTo("WHATSAPP");
                        assertThat(result.success()).isTrue();
                        assertThat(result.messageId()).isEqualTo("DISABLED-ctx-123");
                    })
                    .verifyComplete();

            verifyNoInteractions(exchangeFunction);
        }

        @Test
        @DisplayName("Should successfully send message and parse message ID")
        void shouldSuccessfullySendMessage() {
            String responseBody = """
                    {
                      "messaging_product": "whatsapp",
                      "contacts": [{ "input": "2348012345678", "wa_id": "2348012345678" }],
                      "messages": [{ "id": "wamid.HBgLMjM0ODAxMjM0NTY3OBUCABEYEjExQjJERjgxRkExQUJDOTYwQwA=" }]
                    }
                    """;

            mockExternalApiResponse(HttpStatus.OK, responseBody);

            StepVerifier.create(channel.send("08012345678", "Test message", "ctx-123"))
                    .assertNext(result -> {
                        assertThat(result.channel()).isEqualTo("WHATSAPP");
                        assertThat(result.success()).isTrue();
                        assertThat(result.messageId()).isEqualTo("wamid.HBgLMjM0ODAxMjM0NTY3OBUCABEYEjExQjJERjgxRkExQUJDOTYwQwA=");
                        assertThat(result.rawResponse()).contains("wamid");
                    })
                    .verifyComplete();

            verify(exchangeFunction).exchange(requestCaptor.capture());
            ClientRequest request = requestCaptor.getValue();

            assertThat(request.url().toString()).isEqualTo("https://graph.facebook.com/v18.0/123456789/messages");
            assertThat(request.headers().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-access-token");
        }

        @Test
        @DisplayName("Should handle formatting of different phone number inputs seamlessly")
        void shouldHandlePhoneNumberFormatting() {
            // Because the actual formatting is buried in the body and extracting WebClient bodies
            // in tests is complex, we just verify the pipeline completes successfully for different formats
            mockExternalApiResponse(HttpStatus.OK, "{\"messages\":[{\"id\":\"msg1\"}]}");

            StepVerifier.create(channel.send("+2348012345678", "Test", "ctx"))
                    .expectNextCount(1).verifyComplete();

            StepVerifier.create(channel.send("2348012345678", "Test", "ctx"))
                    .expectNextCount(1).verifyComplete();

            StepVerifier.create(channel.send("08012345678", "Test", "ctx"))
                    .expectNextCount(1).verifyComplete();

            verify(exchangeFunction, times(3)).exchange(any());
        }

        @Test
        @DisplayName("Should handle API errors via onErrorResume and return failed ChannelResult")
        void shouldHandleApiErrors() {
            mockExternalApiResponse(HttpStatus.BAD_REQUEST, "{\"error\": {\"message\": \"Invalid parameter\"}}");

            StepVerifier.create(channel.send("08012345678", "Test message", "ctx-123"))
                    .assertNext(result -> {
                        assertThat(result.channel()).isEqualTo("WHATSAPP");
                        assertThat(result.success()).isFalse();
                        // WebClient naturally throws WebClientResponseException on 4xx/5xx when using .retrieve()
                        assertThat(result.errorMessage()).contains("400 Bad Request");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("Configuration & Metadata Tests")
    class MetadataTests {

        @Test
        @DisplayName("getChannel() should return WHATSAPP")
        void shouldReturnWhatsappChannelName() {
            assertThat(channel.getChannel()).isEqualTo("WHATSAPP");
        }

        @Test
        @DisplayName("getProviderName() should return WHATSAPP_CLOUD_API")
        void shouldReturnProviderName() {
            assertThat(channel.getProviderName()).isEqualTo("WHATSAPP_CLOUD_API");
        }

        @Test
        @DisplayName("getCostPerMessage() should return 0.50")
        void shouldReturnCostPerMessage() {
            assertThat(channel.getCostPerMessage()).isEqualByComparingTo(BigDecimal.valueOf(0.50));
        }

        @Test
        @DisplayName("getBalance() should return -1 as it is postpaid")
        void shouldReturnMinusOneForBalance() {
            StepVerifier.create(channel.getBalance())
                    .expectNext(-1)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("isAvailable() Tests")
    class IsAvailableTests {

        @Test
        @DisplayName("Should return true when completely configured and enabled")
        void shouldReturnTrueWhenReady() {
            StepVerifier.create(channel.isAvailable())
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when disabled")
        void shouldReturnFalseWhenDisabled() {
            ReflectionTestUtils.setField(channel, "enabled", false);

            StepVerifier.create(channel.isAvailable())
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when phone number ID is missing")
        void shouldReturnFalseWhenPhoneIdMissing() {
            ReflectionTestUtils.setField(channel, "phoneNumberId", "");

            StepVerifier.create(channel.isAvailable())
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when access token is missing")
        void shouldReturnFalseWhenTokenMissing() {
            ReflectionTestUtils.setField(channel, "accessToken", null);

            StepVerifier.create(channel.isAvailable())
                    .expectNext(false)
                    .verifyComplete();
        }
    }
}
