package com.fee.app.schoolfeeapp.reporting.utils;

import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.reporting.dto.FeeCollectionReportData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportPdfGenerator Unit Tests")
class ReportPdfGeneratorTest {

    private ReportPdfGenerator generator;

    @Mock
    private FeeCollectionReportData reportData;

    @Mock
    private Payment payment1;

    @Mock
    private Payment payment2;

    @BeforeEach
    void setUp() {
        generator = new ReportPdfGenerator();
    }

    @Nested
    @DisplayName("generateFeeCollectionPdf() Tests")
    class GenerateFeeCollectionPdfTests {

        @Test
        @DisplayName("Should generate complete HTML report with valid data")
        void shouldGenerateCompleteHtmlReport() {
            // Arrange
            when(payment1.getAmount()).thenReturn(BigDecimal.valueOf(5000.00));
            when(payment1.getPaymentMethod()).thenReturn("BANK_TRANSFER");
            when(payment1.getStatus()).thenReturn("COMPLETED");
            when(payment1.getCreatedAt()).thenReturn(Instant.parse("2026-06-08T10:15:30Z"));

            when(payment2.getAmount()).thenReturn(BigDecimal.valueOf(15000.50));
            when(payment2.getPaymentMethod()).thenReturn("CARD");
            when(payment2.getStatus()).thenReturn("PENDING");
            // Test null date fallback
            when(payment2.getCreatedAt()).thenReturn(null);

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("totalAmount", BigDecimal.valueOf(20000.50));
            dataMap.put("totalPayments", 2);
            dataMap.put("payments", List.of(payment1, payment2));

            when(reportData.data()).thenReturn(dataMap);

            // Act
            byte[] resultBytes = generator.generateFeeCollectionPdf(reportData, "school-123");
            String resultHtml = new String(resultBytes, StandardCharsets.UTF_8);

            // Assert - Structure and Headers
            assertThat(resultHtml).contains("<!DOCTYPE html>");
            assertThat(resultHtml).contains("<div class=\"title\">Fee Collection Report</div>");

            // Assert - Summary formatting
            assertThat(resultHtml).contains("<strong>Total Collected:</strong> ₦20,000.50");
            assertThat(resultHtml).contains("<strong>Total Transactions:</strong> 2");

            // Assert - Payment 1
            assertThat(resultHtml).contains("₦5,000.00");
            assertThat(resultHtml).contains("BANK_TRANSFER");
            assertThat(resultHtml).contains("COMPLETED");

            // Assert - Payment 2 (with null date handling)
            assertThat(resultHtml).contains("₦15,000.50");
            assertThat(resultHtml).contains("CARD");
            assertThat(resultHtml).contains("PENDING");
            assertThat(resultHtml).contains("<td>N/A</td>"); // Fallback for null CreatedAt
        }

        @Test
        @DisplayName("Should handle completely empty data map gracefully")
        void shouldHandleEmptyDataMap() {
            // Arrange
            when(reportData.data()).thenReturn(Collections.emptyMap());

            // Act
            byte[] resultBytes = generator.generateFeeCollectionPdf(reportData, "school-123");
            String resultHtml = new String(resultBytes, StandardCharsets.UTF_8);

            // Assert
            assertThat(resultHtml).contains("Fee Collection Report");
            assertThat(resultHtml).contains("<strong>Total Collected:</strong> ₦0.00"); // Default BigDecimal.ZERO
            assertThat(resultHtml).contains("<strong>Total Transactions:</strong> 0"); // Default 0

            // Should contain the table header but no table rows (no <td> elements)
            assertThat(resultHtml).contains("<th>Date</th>");
            assertThat(resultHtml).doesNotContain("<td>");
        }

        @Test
        @DisplayName("Should handle missing payments list but existing summary data")
        void shouldHandleMissingPaymentsList() {
            // Arrange
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("totalAmount", BigDecimal.valueOf(100.00));
            dataMap.put("totalPayments", 5);
            // Intentionally omitting "payments"

            when(reportData.data()).thenReturn(dataMap);

            // Act
            byte[] resultBytes = generator.generateFeeCollectionPdf(reportData, "school-123");
            String resultHtml = new String(resultBytes, StandardCharsets.UTF_8);

            // Assert
            assertThat(resultHtml).contains("<strong>Total Collected:</strong> ₦100.00");
            assertThat(resultHtml).contains("<strong>Total Transactions:</strong> 5");
            assertThat(resultHtml).doesNotContain("<td>"); // No rows should be rendered
        }

        @Test
        @DisplayName("Should handle missing summary data but existing payments list")
        void shouldHandleMissingSummaryWithExistingPayments() {
            // Arrange
            when(payment1.getAmount()).thenReturn(BigDecimal.valueOf(1000));
            when(payment1.getPaymentMethod()).thenReturn("CASH");
            when(payment1.getStatus()).thenReturn("COMPLETED");
            when(payment1.getCreatedAt()).thenReturn(null);

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("payments", List.of(payment1));
            // Intentionally omitting totalAmount and totalPayments

            when(reportData.data()).thenReturn(dataMap);

            // Act
            byte[] resultBytes = generator.generateFeeCollectionPdf(reportData, "school-123");
            String resultHtml = new String(resultBytes, StandardCharsets.UTF_8);

            // Assert
            assertThat(resultHtml).contains("<strong>Total Collected:</strong> ₦0.00"); // Should fallback
            assertThat(resultHtml).contains("<strong>Total Transactions:</strong> 0"); // Should fallback
            assertThat(resultHtml).contains("₦1,000.00"); // But still render the payment row
            assertThat(resultHtml).contains("CASH");
        }
    }
}