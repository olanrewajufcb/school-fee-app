package com.fee.app.schoolfeeapp.notification.channel;

import lombok.Builder;

@Builder
public record ChannelResult(
        String channel,
        String messageId,
        boolean success,
        String errorMessage,
        String rawResponse
) {}