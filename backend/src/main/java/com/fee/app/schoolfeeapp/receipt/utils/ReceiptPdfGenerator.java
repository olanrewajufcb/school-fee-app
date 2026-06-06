package com.fee.app.schoolfeeapp.receipt.utils;

import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.receipt.dto.response.ReceiptDetailResponse;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class ReceiptPdfGenerator {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.of("Africa/Lagos"));

    public byte[] generatePdf(ReceiptDetailResponse receipt) {
        if (receipt == null) {
            throw new SchoolFeeException(
                    "INVALID_RECEIPT_REQUEST",
                    "Receipt details are required");
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, outputStream);
            document.open();

            addHeader(document, receipt);
            addReceiptInfo(document, receipt);
            addBreakdown(document, receipt);
            addFooter(document, receipt);

            document.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new SchoolFeeException(
                    "RECEIPT_PDF_GENERATION_FAILED",
                    "Failed to generate receipt PDF");
        }
    }

    private void addHeader(Document document, ReceiptDetailResponse receipt) throws Exception {
        Font schoolFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
        Paragraph schoolName = new Paragraph(valueOrDash(receipt.schoolName()), schoolFont);
        schoolName.setAlignment(Element.ALIGN_CENTER);
        document.add(schoolName);

        if (receipt.schoolAddress() != null && !receipt.schoolAddress().isBlank()) {
            Paragraph address = new Paragraph(receipt.schoolAddress(),
                    FontFactory.getFont(FontFactory.HELVETICA, 10));
            address.setAlignment(Element.ALIGN_CENTER);
            document.add(address);
        }

        Paragraph title = new Paragraph("PAYMENT RECEIPT",
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(18);
        title.setSpacingAfter(12);
        document.add(title);
    }

    private void addReceiptInfo(Document document, ReceiptDetailResponse receipt) throws Exception {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);

        addInfoRow(table, "Receipt No", receipt.receiptNumber());
        addInfoRow(table, "Payment Date", formatInstant(receipt.paymentDate()));
        addInfoRow(table, "Paid By", receipt.paidBy());
        addInfoRow(table, "Payment Method", receipt.paymentMethod());
        addInfoRow(table, "Amount", formatAmount(receipt.amount()));
        addInfoRow(table, "Amount in Words", receipt.amountInWords());

        document.add(table);
    }

    private void addBreakdown(Document document, ReceiptDetailResponse receipt) throws Exception {
        PdfPTable table = new PdfPTable(new float[] {3, 2, 2});
        table.setWidthPercentage(100);
        table.setSpacingBefore(8);
        table.setSpacingAfter(14);

        addHeaderCell(table, "Student");
        addHeaderCell(table, "Admission No.");
        addHeaderCell(table, "Amount");

        if (receipt.breakdown() != null) {
            for (ReceiptDetailResponse.BreakdownItem item : receipt.breakdown()) {
                addCell(table, valueOrDash(item.studentName()), Element.ALIGN_LEFT);
                addCell(table, valueOrDash(item.admissionNumber()), Element.ALIGN_LEFT);
                addCell(table, formatAmount(item.amount()), Element.ALIGN_RIGHT);
            }
        }

        document.add(table);

        Paragraph total = new Paragraph("Total Paid: " + formatAmount(receipt.amount()),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        total.setAlignment(Element.ALIGN_RIGHT);
        document.add(total);
    }

    private void addFooter(Document document, ReceiptDetailResponse receipt) throws Exception {
        Paragraph footer = new Paragraph(
                "This is a computer-generated receipt. Generated on "
                        + formatInstant(receipt.generatedAt()),
                FontFactory.getFont(FontFactory.HELVETICA, 9));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(24);
        document.add(footer);
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        addCell(table, label, Element.ALIGN_LEFT, true);
        addCell(table, valueOrDash(value), Element.ALIGN_LEFT, false);
    }

    private void addHeaderCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String value, int alignment) {
        addCell(table, value, alignment, false);
    }

    private void addCell(PdfPTable table, String value, int alignment, boolean bold) {
        Font font = FontFactory.getFont(
                bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 10);
        PdfPCell cell = new PdfPCell(new Phrase(valueOrDash(value), font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private String formatAmount(BigDecimal amount) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return String.format("NGN %,.2f", safeAmount);
    }

    private String formatInstant(java.time.Instant instant) {
        return instant != null ? DATE_FORMATTER.format(instant) : "-";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
