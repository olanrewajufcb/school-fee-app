package com.fee.app.schoolfeeapp.reporting.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.reporting.dto.response.DailySummaryResponse;
import com.fee.app.schoolfeeapp.reporting.service.ReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    @Test
    @DisplayName("Should return CSV fee collection report with attachment headers")
    void shouldReturnCsvFeeCollectionReportWithAttachmentHeaders() {
        DataBuffer dataBuffer = new DefaultDataBufferFactory()
                .wrap("Payment ID,Amount\n1,1000\n".getBytes(StandardCharsets.UTF_8));
        when(reportService.generateFeeCollectionReport(
                "current", null, "completed", "CSV"))
                .thenReturn(Mono.just(dataBuffer));

        StepVerifier.create(reportController.getFeeCollectionReport(
                        "current", null, "completed", "csv"))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentType())
                            .isEqualTo(MediaType.parseMediaType("text/csv"));
                    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                            .isEqualTo("attachment; filename=\"fee-collection-report.csv\"");
                    assertThat(response.getBody()).isSameAs(dataBuffer);
                })
                .verifyComplete();

        verify(reportService).generateFeeCollectionReport(
                "current", null, "completed", "CSV");
    }

    @Test
    @DisplayName("Should default blank format to PDF headers")
    void shouldDefaultBlankFormatToPdfHeaders() {
        DataBuffer dataBuffer = new DefaultDataBufferFactory()
                .wrap("<html></html>".getBytes(StandardCharsets.UTF_8));
        when(reportService.generateFeeCollectionReport(
                "current", null, null, "PDF"))
                .thenReturn(Mono.just(dataBuffer));

        StepVerifier.create(reportController.getFeeCollectionReport(
                        "current", null, null, " "))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
                    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                            .isEqualTo("attachment; filename=\"fee-collection-report.pdf\"");
                })
                .verifyComplete();

        verify(reportService).generateFeeCollectionReport(
                "current", null, null, "PDF");
    }

    @Test
    @DisplayName("Should return daily summary response")
    void shouldReturnDailySummaryResponse() {
        LocalDate startDate = LocalDate.parse("2026-06-01");
        LocalDate endDate = LocalDate.parse("2026-06-02");
        DailySummaryResponse serviceResponse = new DailySummaryResponse(
                new DailySummaryResponse.PeriodInfo(startDate, endDate),
                BigDecimal.valueOf(1000),
                1,
                Map.of("PAYSTACK", new DailySummaryResponse.PaymentMethodSummary(
                        BigDecimal.valueOf(1000), 1)),
                List.of(new DailySummaryResponse.DailyBreakdown(
                        startDate, BigDecimal.valueOf(1000), 1)));
        when(reportService.getDailySummary(startDate, endDate)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(reportController.getDailySummary(startDate, endDate))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<DailySummaryResponse> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(reportService).getDailySummary(startDate, endDate);
    }

    @Test
    @DisplayName("Should propagate report service error")
    void shouldPropagateReportServiceError() {
        LocalDate startDate = LocalDate.parse("2026-06-03");
        LocalDate endDate = LocalDate.parse("2026-06-01");
        SchoolFeeException expectedError = new SchoolFeeException(
                "INVALID_REPORT_DATE_RANGE",
                "Start date cannot be after end date");
        when(reportService.getDailySummary(startDate, endDate)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(reportController.getDailySummary(startDate, endDate))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(reportService).getDailySummary(startDate, endDate);
    }
}
