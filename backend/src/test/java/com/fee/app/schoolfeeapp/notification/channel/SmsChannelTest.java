package com.fee.app.schoolfeeapp.notification.channel;

import com.fee.app.schoolfeeapp.notification.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SmsChannel Unit Tests")
class SmsChannelTest {

    @Mock
    private SmsService smsService;

    private SmsChannel smsChannel;

    @BeforeEach
    void setUp() {
        smsChannel = new SmsChannel(smsService);
    }

    @Nested
    @DisplayName("Metadata & Configuration Tests")
    class MetadataTests {

        @Test
        @DisplayName("Should return correct channel name")
        void shouldReturnCorrectChannelName() {
            assertThat(smsChannel.getChannel()).isEqualTo("SMS");
        }

        @Test
        @DisplayName("Should return correct provider name")
        void shouldReturnCorrectProviderName() {
            assertThat(smsChannel.getProviderName()).isEqualTo("AFRICAS_TALKING");
        }

        @Test
        @DisplayName("Should return correct cost per message")
        void shouldReturnCorrectCostPerMessage() {
            assertThat(smsChannel.getCostPerMessage()).isEqualByComparingTo(BigDecimal.valueOf(4.00));
        }
    }

    @Nested
    @DisplayName("send() Tests")
    class SendTests {

        @Test
        @DisplayName("Should successfully send SMS and map to positive ChannelResult")
        void shouldSuccessfullySendSms() {
            // Arrange
            String recipient = "+2348012345678";
            String message = "Hello, your fee is due.";
            String contextId = "fee-123";

            when(smsService.send(recipient, message)).thenReturn(Mono.empty());

            // Act & Assert
            StepVerifier.create(smsChannel.send(recipient, message, contextId))
                    .assertNext(result -> {
                        assertThat(result.channel()).isEqualTo("SMS");
                        assertThat(result.success()).isTrue();
                        assertThat(result.messageId()).isEqualTo("SMS-fee-123");
                        assertThat(result.errorMessage()).isNull();
                    })
                    .verifyComplete();

            verify(smsService).send(recipient, message);
        }

        @Test
        @DisplayName("Should handle SMS service errors and map to negative ChannelResult")
        void shouldHandleSmsErrorsGracefully() {
            // Arrange
            String recipient = "+2348012345678";
            String message = "Hello, your fee is due.";
            String contextId = "fee-123";

            when(smsService.send(recipient, message))
                    .thenReturn(Mono.error(new RuntimeException("Gateway Timeout")));

            // Act & Assert
            StepVerifier.create(smsChannel.send(recipient, message, contextId))
                    .assertNext(result -> {
                        assertThat(result.channel()).isEqualTo("SMS");
                        assertThat(result.success()).isFalse();
                        assertThat(result.messageId()).isNull();
                        assertThat(result.errorMessage()).isEqualTo("Gateway Timeout");
                    })
                    .verifyComplete();

            verify(smsService).send(recipient, message);
        }
    }

    @Nested
    @DisplayName("getBalance() Tests")
    class GetBalanceTests {

        @Test
        @DisplayName("Should retrieve balance directly from SmsService")
        void shouldRetrieveBalanceFromService() {
            when(smsService.getBalance()).thenReturn(Mono.just(2500));

            StepVerifier.create(smsChannel.getBalance())
                    .expectNext(2500)
                    .verifyComplete();

            verify(smsService).getBalance();
        }

        @Test
        @DisplayName("Should propagate errors from SmsService when getting balance")
        void shouldPropagateErrorsFromService() {
            when(smsService.getBalance())
                    .thenReturn(Mono.error(new RuntimeException("API Down")));

            StepVerifier.create(smsChannel.getBalance())
                    .expectErrorMessage("API Down")
                    .verify();

            verify(smsService).getBalance();
        }
    }

    @Nested
    @DisplayName("isAvailable() Tests")
    class IsAvailableTests {

        @Test
        @DisplayName("Should return true when balance is positive")
        void shouldReturnTrueWhenBalancePositive() {
            when(smsService.getBalance()).thenReturn(Mono.just(100));

            StepVerifier.create(smsChannel.isAvailable())
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return true when balance is exactly zero")
        void shouldReturnTrueWhenBalanceZero() {
            when(smsService.getBalance()).thenReturn(Mono.just(0));

            StepVerifier.create(smsChannel.isAvailable())
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false when balance is negative")
        void shouldReturnFalseWhenBalanceNegative() {
            when(smsService.getBalance()).thenReturn(Mono.just(-1));

            StepVerifier.create(smsChannel.isAvailable())
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return false and recover gracefully if balance check throws an error")
        void shouldReturnFalseOnServiceError() {
            when(smsService.getBalance())
                    .thenReturn(Mono.error(new RuntimeException("Network Timeout")));

            StepVerifier.create(smsChannel.isAvailable())
                    .expectNext(false) // Triggered by .onErrorReturn(false)
                    .verifyComplete();
        }
    }
}