package com.fee.app.schoolfeeapp.reporting.dto.request;

import java.util.UUID;

/**
 * Parameters for fee collection report generation.
 * Used internally, not exposed as a request body.
 */
public record FeeCollectionReportRequest(
        UUID termId,
        UUID classId,
        String status,
        String format
) {}