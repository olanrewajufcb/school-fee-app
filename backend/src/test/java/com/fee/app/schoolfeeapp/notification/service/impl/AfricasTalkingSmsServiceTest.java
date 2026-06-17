package com.fee.app.schoolfeeapp.notification.service.impl;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.common.exceptions.SmsSendException;
import com.fee.app.schoolfeeapp.common.utils.PhoneNumberNormalizer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AfricasTalkingSmsService Unit Tests")
class AfricasTalkingSmsServiceTest {

    private AfricasTalkingSmsService smsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ExchangeFunction exchangeFunction;

    @Captor
    private ArgumentCaptor<ClientRequest> requestCaptor;

    private MockedStatic<PhoneNumberNormalizer> mockedNormalizer;

    @BeforeEach
    void setUp() {
        smsService = new AfricasTalkingSmsService(objectMapper);

        // Inject configuration properties
        ReflectionTestUtils.setField(smsService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(smsService, "username", "test-username");
        ReflectionTestUtils.setField(smsService, "senderId", "SCHOOLFEE");
        ReflectionTestUtils.setField(smsService, "defaultCountryCode", "+234");
        ReflectionTestUtils.setField(smsService, "enabled", true); // Default to true for most tests

        // Inject mocked WebClient
        WebClient mockWebClient = WebClient.builder()
                .baseUrl("https://api.africastalking.com")
                .exchangeFunction(exchangeFunction)
                .build();
        ReflectionTestUtils.setField(smsService, "webClient", mockWebClient);

        // Setup static mock for PhoneNumberNormalizer
        mockedNormalizer = mockStatic(PhoneNumberNormalizer.class);
        mockedNormalizer.when(() -> PhoneNumberNormalizer.normalize(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0)); // Default: return input as is
    }

    @AfterEach
    void tearDown() {
        // Must close the static mock to prevent leakage across tests
        if (mockedNormalizer != null) {
            mockedNormalizer.close();
        }
    }

    private void mockExternalApiResponse(HttpStatus status, String jsonBody) {
        ClientResponse mockResponse = ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(jsonBody)
                .build();
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(mockResponse));
    }

    @Nested
    @DisplayName("send() Tests")
    class SendTests {

        @Test
        @DisplayName("Should successfully send SMS when gateway returns 101 Success")
        void shouldSuccessfullySendSms() {
            String phoneNumber = "+2348012345678";
            String message = "Hello, your fee is due.";
            String responseBody = """
                    {
                      "SMSMessageData": {
                        "Message": "Sent to 1/1 Total Cost: KES 0.8000",
                        "Recipients": [{
                          "statusCode": 101,
                          "number": "+2348012345678",
                          "status": "Success",
                          "cost": "KES 0.8000",
                          "messageId": "ATXid_abc123"
                        }]
                      }
                    }
                    """;

            // Note: The service expects HTTP 201 CREATED
            mockExternalApiResponse(HttpStatus.CREATED, responseBody);

            StepVerifier.create(smsService.send(phoneNumber, message))
                    .verifyComplete();

            verify(exchangeFunction).exchange(requestCaptor.capture());
            ClientRequest capturedRequest = requestCaptor.getValue();

            assertThat(capturedRequest.url().toString()).isEqualTo("https://api.africastalking.com/version1/messaging");
            assertThat(capturedRequest.headers().getFirst("apiKey")).isEqualTo("test-api-key");

            // Note: In reality, Spring WebClient body extraction from ClientRequest is complex in tests,
            // so we rely on verifying the endpoint and headers, and the fact that the mock matched.
        }

        @Test
        @DisplayName("Should not send SMS when service is disabled")
        void shouldNotSendSmsWhenDisabled() {
            ReflectionTestUtils.setField(smsService, "enabled", false);

            StepVerifier.create(smsService.send("+2348012345678", "Test message"))
                    .verifyComplete();

            // Verify no external calls were made
            verifyNoInteractions(exchangeFunction);
        }

        @Test
        @DisplayName("Should not send SMS when phone format is invalid")
        void shouldNotSendSmsWhenPhoneIsInvalid() {
            String invalidPhone = "080123";

            // Mock normalizer returning null for invalid format
            mockedNormalizer.when(() -> PhoneNumberNormalizer.normalize(invalidPhone)).thenReturn(null);

            StepVerifier.create(smsService.send(invalidPhone, "Test message"))
                    .verifyComplete();

            verifyNoInteractions(exchangeFunction);
        }

        @Test
        @DisplayName("Should handle non-201 HTTP status code from gateway")
        void shouldHandleHttpErrorFromGateway() {
            mockExternalApiResponse(HttpStatus.BAD_REQUEST, "{\"error\": \"Invalid credentials\"}");

            StepVerifier.create(smsService.send("+2348012345678", "Test message"))
                    .expectErrorMatches(throwable -> throwable instanceof SmsSendException &&
                            throwable.getMessage().contains("SMS send failed with status: 400"))
                    .verify();
        }

        @Test
        @DisplayName("Should handle 201 HTTP status but gateway rejection (statusCode != 101)")
        void shouldHandleGatewayRejection() {
            String responseBody = """
                    {
                      "SMSMessageData": {
                        "Recipients": [{
                          "statusCode": 403,
                          "number": "+2348012345678",
                          "status": "User Not Found",
                          "cost": "KES 0.0000",
                          "messageId": "None"
                        }]
                      }
                    }
                    """;

            mockExternalApiResponse(HttpStatus.CREATED, responseBody);

            StepVerifier.create(smsService.send("+2348012345678", "Test message"))
                    .expectErrorMatches(throwable -> throwable instanceof SmsSendException &&
                            throwable.getMessage().contains("SMS rejected by gateway. Status: User Not Found (403)"))
                    .verify();
        }

        @Test
        @DisplayName("Should handle malformed response missing recipients array")
        void shouldHandleMalformedResponse() {
            String responseBody = """
                    {
                      "SMSMessageData": {
                        "Message": "Malformed data"
                      }
                    }
                    """;

            mockExternalApiResponse(HttpStatus.CREATED, responseBody);

            StepVerifier.create(smsService.send("+2348012345678", "Test message"))
                    .expectErrorMatches(throwable -> throwable instanceof SmsSendException &&
                            throwable.getMessage().contains("No recipients in SMS response"))
                    .verify();
        }
    }

    @Nested
    @DisplayName("getBalance() Tests")
    class GetBalanceTests {

        @Test
        @DisplayName("Should successfully fetch and parse SMS balance")
        void shouldSuccessfullyFetchBalance() {
            String responseBody = """
                    {
                      "UserData": {
                        "balance": "KES 2500.0000"
                      }
                    }
                    """;

            mockExternalApiResponse(HttpStatus.OK, responseBody);

            StepVerifier.create(smsService.getBalance())
                    .expectNext(2500)
                    .verifyComplete();

            verify(exchangeFunction).exchange(requestCaptor.capture());
            assertThat(requestCaptor.getValue().url().toString())
                    .isEqualTo("https://api.africastalking.com/version1/user?username=test-username");
        }

        @Test
        @DisplayName("Should return 2500 default when disabled")
        void shouldReturnDefaultWhenDisabled() {
            ReflectionTestUtils.setField(smsService, "enabled", false);

            StepVerifier.create(smsService.getBalance())
                    .expectNext(2500)
                    .verifyComplete();

            verifyNoInteractions(exchangeFunction);
        }

        @Test
        @DisplayName("Should return -1 when API returns an error")
        void shouldReturnMinusOneOnError() {
            mockExternalApiResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Gateway timeout");

            StepVerifier.create(smsService.getBalance())
                    .expectNext(-1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return -1 when parsing fails")
        void shouldReturnMinusOneOnParseFailure() {
            String malformedResponseBody = """
                    {
                      "UserData": {
                        "balance": "INVALID_FORMAT"
                      }
                    }
                    """;

            mockExternalApiResponse(HttpStatus.OK, malformedResponseBody);

            StepVerifier.create(smsService.getBalance())
                    .expectNext(-1)
                    .verifyComplete();
        }
    }
}
