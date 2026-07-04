package com.fee.app.schoolfeeapp.receipt.utils;

import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.receipt.dto.response.ReceiptDetailResponse;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptPdfGeneratorTest {

    private final ReceiptPdfGenerator generator = new ReceiptPdfGenerator();

    @Test
    void shouldThrowExceptionWhenReceiptIsNull() {
        assertThatThrownBy(() -> generator.generatePdf(null))
                .isInstanceOf(SchoolFeeException.class)
                .hasMessageContaining("Receipt details are required")
                .matches(ex -> ((SchoolFeeException) ex).getErrorCode().equals("INVALID_RECEIPT_REQUEST"));
    }

    @Test
    void shouldGeneratePdfSuccessfullyForValidReceipt() throws Exception {
        ReceiptDetailResponse receipt = new ReceiptDetailResponse(
                "REC-12345",
                UUID.randomUUID(),
                "Grace International School",
                "123 School Road, Lagos",
                "Alice Johnson",
                new BigDecimal("50000.00"),
                "Fifty Thousand Naira Only",
                "PAYSTACK",
                Instant.parse("2026-06-01T10:00:00Z"),
                List.of(
                        new ReceiptDetailResponse.BreakdownItem(
                                "John Doe", "ADM001", "JSS 1", "Term 1", new BigDecimal("25000.00")),
                        new ReceiptDetailResponse.BreakdownItem(
                                "Jane Doe", "ADM002", "JSS 2", "Term 1", new BigDecimal("25000.00"))
                ),
                Instant.parse("2026-06-01T10:05:00Z"),
                true,
                true
        );

        byte[] pdfBytes = generator.generatePdf(receipt);
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes).startsWith("%PDF".getBytes());

        try (PdfReader reader = new PdfReader(pdfBytes)) {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void shouldGeneratePdfSuccessfullyWithMissingOptionalFields() throws Exception {
        ReceiptDetailResponse receipt = new ReceiptDetailResponse(
                "REC-12345",
                UUID.randomUUID(),
                null, // null schoolName
                "", // empty schoolAddress
                "Alice Johnson",
                null, // null amount
                "Zero Naira",
                "CASH",
                null, // null paymentDate
                null, // null breakdown
                null, // null generatedAt
                false,
                false
        );

        byte[] pdfBytes = generator.generatePdf(receipt);
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes).startsWith("%PDF".getBytes());

        try (PdfReader reader = new PdfReader(pdfBytes)) {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void shouldHandleEmptyOrNullFieldsInBreakdown() throws Exception {
        ReceiptDetailResponse receipt = new ReceiptDetailResponse(
                "REC-12345",
                UUID.randomUUID(),
                " ", // blank schoolName
                "   ", // blank schoolAddress
                "Alice Johnson",
                new BigDecimal("10000.00"),
                "Ten Thousand Naira",
                "BANK_TRANSFER",
                Instant.now(),
                List.of(
                        new ReceiptDetailResponse.BreakdownItem(
                                null, // null studentName
                                "", // empty admissionNumber
                                "JSS 1",
                                "Term 1",
                                new BigDecimal("10000.00")
                        )
                ),
                Instant.now(),
                true,
                true
        );

        byte[] pdfBytes = generator.generatePdf(receipt);
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes).startsWith("%PDF".getBytes());
    }

    @Test
    void shouldThrowExceptionWhenPdfGenerationFails() {
        ReceiptDetailResponse corruptReceipt = org.mockito.Mockito.mock(ReceiptDetailResponse.class);
        org.mockito.Mockito.when(corruptReceipt.breakdown()).thenThrow(new RuntimeException("Simulated PDF generation error"));

        assertThatThrownBy(() -> generator.generatePdf(corruptReceipt))
                .isInstanceOf(SchoolFeeException.class)
                .hasMessageContaining("Failed to generate receipt PDF")
                .matches(ex -> ((SchoolFeeException) ex).getErrorCode().equals("RECEIPT_PDF_GENERATION_FAILED"));
    }
}
