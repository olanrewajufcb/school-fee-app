package com.fee.app.schoolfeeapp.receipt.dto.response;

import java.time.Instant;

public record ShareReceiptResponse(
        String channel,
        Instant sentAt,
        String message
) {}