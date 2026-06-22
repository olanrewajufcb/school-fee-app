package com.fee.app.schoolfeeapp.result.utils;

import com.fee.app.schoolfeeapp.result.dto.response.StudentResultResponse;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class ResultPdfGenerator {

    private static final Color NAVY = new Color(15, 23, 42);
    private static final Color SLATE = new Color(71, 85, 105);
    private static final Color LIGHT = new Color(241, 245, 249);
    private static final Color BORDER = new Color(203, 213, 225);
    private static final Color GREEN = new Color(5, 150, 105);

    public byte[] generateStudentResultPdf(StudentResultResponse result, String schoolName) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 32, 32, 30, 30);
            PdfWriter.getInstance(document, output);
            document.open();

            addHeader(document, result, schoolName);
            addSummary(document, result);
            addSubjectTable(document, result);
            addComments(document, result);
            addFooter(document);

            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to generate student result PDF", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to close student result PDF", exception);
        }
    }

    private void addHeader(Document document, StudentResultResponse result, String schoolName)
            throws DocumentException {
        Font schoolFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, NAVY);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, GREEN);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9, SLATE);

        Paragraph school = new Paragraph(
                schoolName == null || schoolName.isBlank() ? "SchoolFee School" : schoolName,
                schoolFont);
        school.setAlignment(Element.ALIGN_CENTER);
        document.add(school);

        Paragraph title = new Paragraph("STUDENT REPORT CARD", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(3);
        document.add(title);

        String term = result.term() == null
                ? "Term"
                : result.term().name() + " - " + result.term().sessionName();
        Paragraph termLine = new Paragraph(term, bodyFont);
        termLine.setAlignment(Element.ALIGN_CENTER);
        termLine.setSpacingAfter(14);
        document.add(termLine);

        PdfPTable identity = new PdfPTable(new float[]{1.4f, 1f, 1f, 1f});
        identity.setWidthPercentage(100);
        StudentResultResponse.StudentInfo student = result.student();
        identity.addCell(infoCell("Student", student == null ? "-" : student.fullName()));
        identity.addCell(infoCell("Admission No.", student == null ? "-" : student.admissionNumber()));
        identity.addCell(infoCell("Class", student == null ? "-" : student.className()));
        identity.addCell(infoCell(
                "Position",
                result.ranking() == null
                        ? "-"
                        : ordinal(result.ranking().classPosition()) + " of " + result.ranking().outOf()));
        identity.setSpacingAfter(12);
        document.add(identity);
    }

    private void addSummary(Document document, StudentResultResponse result)
            throws DocumentException {
        StudentResultResponse.ResultSummary summary = result.summary();
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        table.addCell(summaryCell("Total Score", summary == null
                ? "-"
                : decimal(summary.totalScore()) + " / " + summary.totalMaxScore()));
        table.addCell(summaryCell("Average", summary == null
                ? "-"
                : decimal(summary.average()) + "%"));
        table.addCell(summaryCell("Overall Grade", summary == null
                ? "-"
                : safe(summary.overallGrade())));
        table.addCell(summaryCell("Subjects Passed", summary == null
                ? "-"
                : summary.subjectsPassed() + " / " + summary.subjectsTaken()));
        table.addCell(summaryCell("Attendance", result.attendance() == null
                ? "-"
                : String.format("%.1f%%", result.attendance().attendanceRate())));
        document.add(table);
    }

    private void addSubjectTable(Document document, StudentResultResponse result)
            throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{2.2f, 1f, 1f, 1f, 0.8f, 0.7f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);

        addHeaderCell(table, "Subject");
        addHeaderCell(table, "CA");
        addHeaderCell(table, "Exam");
        addHeaderCell(table, "Final");
        addHeaderCell(table, "Grade");
        addHeaderCell(table, "Pos.");

        for (StudentResultResponse.SubjectResult subject : result.subjects()) {
            addBodyCell(table, subject.subjectName(), Element.ALIGN_LEFT);
            addBodyCell(table, decimal(subject.caTotal()) + " / " + subject.caMaxTotal(), Element.ALIGN_CENTER);
            addBodyCell(table, decimal(subject.examScore()) + " / " + subject.examMaxScore(), Element.ALIGN_CENTER);
            addBodyCell(table, decimal(subject.finalScore()), Element.ALIGN_CENTER);
            addBodyCell(table, safe(subject.grade()), Element.ALIGN_CENTER);
            addBodyCell(
                    table,
                    subject.subjectPosition() > 0 ? ordinal(subject.subjectPosition()) : "-",
                    Element.ALIGN_CENTER);
        }
        table.setSpacingAfter(12);
        document.add(table);
    }

    private void addComments(Document document, StudentResultResponse result)
            throws DocumentException {
        PdfPTable comments = new PdfPTable(2);
        comments.setWidthPercentage(100);
        comments.addCell(commentCell("Teacher's Comment", result.teacherComment()));
        comments.addCell(commentCell("Principal's Comment", result.principalComment()));
        document.add(comments);
    }

    private void addFooter(Document document) throws DocumentException {
        Paragraph footer = new Paragraph(
                "Generated by SchoolFee on "
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                FontFactory.getFont(FontFactory.HELVETICA, 8, SLATE));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(15);
        document.add(footer);
    }

    private PdfPCell infoCell(String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBackgroundColor(LIGHT);
        cell.setBorderColor(BORDER);
        cell.addElement(new Phrase(label, FontFactory.getFont(FontFactory.HELVETICA, 7, SLATE)));
        cell.addElement(new Phrase(safe(value), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, NAVY)));
        return cell;
    }

    private PdfPCell summaryCell(String label, String value) {
        return infoCell(label, value);
    }

    private PdfPCell commentCell(String title, String text) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        cell.setBorderColor(BORDER);
        cell.addElement(new Phrase(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, NAVY)));
        cell.addElement(new Phrase(
                text == null || text.isBlank() ? "No comment provided." : text,
                FontFactory.getFont(FontFactory.HELVETICA, 9, SLATE)));
        return cell;
    }

    private void addHeaderCell(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(
                value,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)));
        cell.setBackgroundColor(NAVY);
        cell.setPadding(7);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBorderColor(NAVY);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String value, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(
                safe(value),
                FontFactory.getFont(FontFactory.HELVETICA, 8, NAVY)));
        cell.setPadding(7);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(BORDER);
        cell.setBorder(Rectangle.BOX);
        table.addCell(cell);
    }

    private String decimal(java.math.BigDecimal value) {
        return value == null ? "0.0" : value.stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String ordinal(int value) {
        int mod100 = value % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return value + "th";
        }
        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }
}
