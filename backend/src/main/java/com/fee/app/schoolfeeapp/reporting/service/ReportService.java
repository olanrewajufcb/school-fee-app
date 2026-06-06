package com.fee.app.schoolfeeapp.reporting.service;

import com.fee.app.schoolfeeapp.reporting.dto.response.DailySummaryResponse;
import org.springframework.core.io.buffer.DataBuffer;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ReportService {

    /**
     * Generate a fee collection report.
     *
     * @param termId Term UUID or "current"
     * @param classId Optional class filter
     * @param status Optional status filter (PENDING, PARTIAL, PAID, OVERDUE)
     * @param format Output format (PDF, EXCEL, CSV)
     * @return Report file as byte buffer
     */
    Mono<DataBuffer> generateFeeCollectionReport(String termId, String classId, String status, String format);

    /**
     * Get daily collection summary for a date range.
     */
    Mono<DailySummaryResponse> getDailySummary(LocalDate startDate, LocalDate endDate);
}