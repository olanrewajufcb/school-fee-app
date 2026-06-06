package com.fee.app.schoolfeeapp.notification.channel;

import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NotificationChannelSelector {

    private final Map<String, NotificationChannel> channelMap;

    public NotificationChannelSelector(List<NotificationChannel> channels) {
        this.channelMap = channels.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NotificationChannel::getChannel,
                        channel -> channel));
    }

    public NotificationChannel select(String channel) {
        NotificationChannel selected = channelMap.get(channel.toUpperCase());
        if (selected == null) {
            throw new SchoolFeeException(
                    "UNSUPPORTED_CHANNEL",
                    "No channel configured for: " + channel);
        }
        return selected;
    }

    public List<String> getAvailableChannels() {
        return List.copyOf(channelMap.keySet());
    }
}