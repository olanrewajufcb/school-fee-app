package com.fee.app.schoolfeeapp.notification.channel;

import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationChannelSelector Unit Tests")
class NotificationChannelSelectorTest {

    @Mock
    private NotificationChannel smsChannel;

    @Mock
    private NotificationChannel whatsappChannel;

    private NotificationChannelSelector selector;

    @BeforeEach
    void setUp() {
        // Mock the getChannel() method so the selector's constructor can build its internal map
        when(smsChannel.getChannel()).thenReturn("SMS");
        when(whatsappChannel.getChannel()).thenReturn("WHATSAPP");

        // Initialize the selector with our mocked channels
        selector = new NotificationChannelSelector(List.of(smsChannel, whatsappChannel));
    }

    @Nested
    @DisplayName("select() Tests")
    class SelectTests {

        @Test
        @DisplayName("Should return the correct channel for an exact match")
        void shouldReturnCorrectChannelForExactMatch() {
            NotificationChannel result = selector.select("SMS");

            assertThat(result).isNotNull();
            assertThat(result.getChannel()).isEqualTo("SMS");
            assertThat(result).isSameAs(smsChannel);
        }

        @Test
        @DisplayName("Should return the correct channel using case-insensitive lookup")
        void shouldReturnCorrectChannelCaseInsensitive() {
            NotificationChannel result = selector.select("whatsapp");

            assertThat(result).isNotNull();
            assertThat(result.getChannel()).isEqualTo("WHATSAPP");
            assertThat(result).isSameAs(whatsappChannel);
        }

        @Test
        @DisplayName("Should throw SchoolFeeException for an unknown channel")
        void shouldThrowExceptionForUnknownChannel() {
            assertThatThrownBy(() -> selector.select("EMAIL"))
                    .isInstanceOf(SchoolFeeException.class)
                    .matches(ex -> ((SchoolFeeException) ex).getErrorCode().equals("UNSUPPORTED_CHANNEL"))
                    .hasMessageContaining("No channel configured for: EMAIL");
        }

        @Test
        @DisplayName("Should throw NullPointerException if null is passed to select")
        void shouldThrowNpeForNullChannel() {
            // Because your code calls channel.toUpperCase() without a null check
            assertThatThrownBy(() -> selector.select(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("getAvailableChannels() Tests")
    class GetAvailableChannelsTests {

        @Test
        @DisplayName("Should return exactly the registered channel keys")
        void shouldReturnRegisteredChannelKeys() {
            List<String> availableChannels = selector.getAvailableChannels();

            assertThat(availableChannels)
                    .isNotNull()
                    .hasSize(2)
                    .containsExactlyInAnyOrder("SMS", "WHATSAPP");
        }

        @Test
        @DisplayName("Should return an empty list if initialized with no channels")
        void shouldReturnEmptyListIfNoChannels() {
            NotificationChannelSelector emptySelector = new NotificationChannelSelector(List.of());

            assertThat(emptySelector.getAvailableChannels()).isEmpty();
        }
    }
}