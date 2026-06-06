package com.fee.app.schoolfeeapp.reporting.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.reporting.dto.response.DailySummaryResponse;
import com.fee.app.schoolfeeapp.reporting.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * GET /api/v1/reports/fee-collection
     * Generate fee collection report.
     */
    @GetMapping("/fee-collection")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<DataBuffer>> getFeeCollectionReport(
            @RequestParam(required = false, defaultValue = "current") String termId,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "PDF") String format) {

        String normalizedFormat = format == null || format.isBlank()
                ? "PDF"
                : format.trim().toUpperCase(Locale.ROOT);

        return reportService.generateFeeCollectionReport(termId, classId, status, normalizedFormat)
                .map(reportData -> {
                    String contentType = "PDF".equals(normalizedFormat)
                            ? MediaType.APPLICATION_PDF_VALUE
                            : "text/csv";
                    String extension = "PDF".equals(normalizedFormat) ? "pdf" : "csv";

                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION,
                                    "attachment; filename=\"fee-collection-report." + extension + "\"")
                            .contentType(MediaType.parseMediaType(contentType))
                            .body(reportData);
                });
    }

    /**
     * GET /api/v1/reports/daily-summary
     * Get daily collection summary.
     */
    @GetMapping("/daily-summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<DailySummaryResponse>>> getDailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return reportService.getDailySummary(startDate, endDate)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }
}
