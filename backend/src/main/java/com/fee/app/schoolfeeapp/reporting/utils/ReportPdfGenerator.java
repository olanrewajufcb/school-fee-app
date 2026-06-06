package com.fee.app.schoolfeeapp.reporting.utils;

import com.fee.app.schoolfeeapp.payment.domain.Payment;
import com.fee.app.schoolfeeapp.reporting.dto.FeeCollectionReportData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
public class ReportPdfGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    /**
     * Generate a PDF fee collection report.
     * MVP: Returns HTML wrapped as bytes.
     * Phase 2: Use iText or Flying Saucer for proper PDF generation.
     */
    public byte[] generateFeeCollectionPdf(FeeCollectionReportData data, String schoolId) {
        String html = buildReportHtml(data);
        return html.getBytes();
    }

    private String buildReportHtml(FeeCollectionReportData data) {
        BigDecimal totalAmount = (BigDecimal) data.data().getOrDefault("totalAmount", BigDecimal.ZERO);
        int totalPayments = (int) data.data().getOrDefault("totalPayments", 0);

        @SuppressWarnings("unchecked")
        List<Payment> payments = (List<Payment>) data.data().get("payments");

        StringBuilder rows = new StringBuilder();
        if (payments != null) {
            for (Payment p : payments) {
                rows.append(String.format("""
                    <tr>
                        <td>%s</td>
                        <td>₦%,.2f</td>
                        <td>%s</td>
                        <td>%s</td>
                    </tr>
                    """,
                        p.getCreatedAt() != null
                                ? p.getCreatedAt().atZone(java.time.ZoneId.systemDefault())
                                .format(DATE_FORMATTER) : "N/A",
                        p.getAmount(),
                        p.getPaymentMethod(),
                        p.getStatus()));
            }
        }

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: Arial, sans-serif; margin: 30px; }
                    .header { text-align: center; margin-bottom: 25px; }
                    .title { font-size: 20px; font-weight: bold; }
                    .summary { margin: 20px 0; padding: 15px; background: #f5f5f5; }
                    .summary-item { display: inline-block; margin-right: 30px; }
                    table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                    th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
                    th { background-color: #4CAF50; color: white; }
                    .footer { margin-top: 30px; text-align: center; font-size: 11px; color: #888; }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="title">Fee Collection Report</div>
                    <div>Generated: %s</div>
                </div>
                
                <div class="summary">
                    <span class="summary-item"><strong>Total Collected:</strong> ₦%,.2f</span>
                    <span class="summary-item"><strong>Total Transactions:</strong> %d</span>
                </div>
                
                <table>
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Amount</th>
                            <th>Method</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>
                
                <div class="footer">
                    SchoolFee — Automated Report. Generated on %s
                </div>
            </body>
            </html>
            """,
                java.time.LocalDateTime.now().format(DATE_FORMATTER),
                totalAmount,
                totalPayments,
                rows.toString(),
                java.time.LocalDateTime.now().format(DATE_FORMATTER));
    }
}