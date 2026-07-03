package com.fee.app.schoolfeeapp.result.utils;

import com.fee.app.schoolfeeapp.result.dto.response.StudentResultResponse;
import com.lowagie.text.pdf.PdfReader;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResultPdfGeneratorTest {

    private final ResultPdfGenerator generator = new ResultPdfGenerator();

    @Test
    void shouldGenerateAReadablePdfReportCard() throws Exception {
        StudentResultResponse result = new StudentResultResponse(
                new StudentResultResponse.StudentInfo(
                        UUID.randomUUID(), "STU2600038E7D", "Hamm Had", "JSS 1", 2, null),
                new StudentResultResponse.TermInfo(
                        UUID.randomUUID(), "First Term", "2025/2026"),
                List.of(
                        subject("English Language", 20, 55, 75, "A", 1),
                        subject("Mathematics", 18, 37, 55, "C", 2)),
                new StudentResultResponse.ResultSummary(
                        BigDecimal.valueOf(130), 200, BigDecimal.valueOf(65),
                        "B", BigDecimal.valueOf(8), 2, 2, 0,
                        BigDecimal.valueOf(62), BigDecimal.valueOf(75),
                        BigDecimal.valueOf(55), "Promoted to JSS 2"),
                new StudentResultResponse.RankingInfo(1, 2, 100, true),
                new StudentResultResponse.AttendanceInfo(40, 38, 2, 95),
                List.of(
                        new StudentResultResponse.GradeScaleItem("A", BigDecimal.valueOf(75), BigDecimal.valueOf(100), "Excellent"),
                        new StudentResultResponse.GradeScaleItem("B", BigDecimal.valueOf(70), BigDecimal.valueOf(74), "Very Good")),
                List.of(new StudentResultResponse.TraitAssessment("Punctuality", "BEHAVIOURAL", "A", "Always on time")),
                List.of(new StudentResultResponse.TraitAssessment("Handwriting", "PSYCHOMOTOR", "B", "Good control")),
                "A focused and diligent learner.",
                "Excellent progress. Keep it up.");

        byte[] pdf = generator.generateStudentResultPdf(result, "Success School");

        assertThat(pdf).startsWith("%PDF".getBytes());
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }

        String previewPath = System.getenv("PDF_PREVIEW_PATH");
        if (previewPath != null && !previewPath.isBlank()) {
            Files.write(Path.of(previewPath), pdf);
        }
    }

    @Test
    void shouldHandleNullOrBlankSchoolName() throws Exception {
        StudentResultResponse result = createBaseResult();
        
        // 1. null schoolName
        byte[] pdfNull = generator.generateStudentResultPdf(result, null);
        assertThat(pdfNull).isNotEmpty();
        
        // 2. blank schoolName
        byte[] pdfBlank = generator.generateStudentResultPdf(result, "   ");
        assertThat(pdfBlank).isNotEmpty();
    }

    @Test
    void shouldHandleNullTermAndRankingAndSummary() throws Exception {
        StudentResultResponse result = new StudentResultResponse(
                new StudentResultResponse.StudentInfo(
                        UUID.randomUUID(), "STU123", "Test Student", "Class A", 1, null),
                null, // null term
                List.of(subject("English Language", 20, 55, 75, "A", 0)), // subjectPosition <= 0
                null, // null summary
                null, // null ranking
                null, // null attendance
                null, // null gradingScale
                null, // null behavioural
                null, // null psychomotor
                null, // null teacherComment
                null  // null principalComment
        );
        byte[] pdf = generator.generateStudentResultPdf(result, "Test School");
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void shouldHandleNullOrEmptyGradingScale() throws Exception {
        StudentResultResponse result1 = new StudentResultResponse(
                new StudentResultResponse.StudentInfo(UUID.randomUUID(), "STU123", "Test", "Class A", 1, null),
                new StudentResultResponse.TermInfo(UUID.randomUUID(), "First Term", "2025/2026"),
                List.of(subject("English Language", 20, 55, 75, "A", 1)),
                new StudentResultResponse.ResultSummary(BigDecimal.valueOf(75), 100, BigDecimal.valueOf(75), "A", BigDecimal.valueOf(1), 1, 1, 0, BigDecimal.valueOf(75), BigDecimal.valueOf(75), BigDecimal.valueOf(75), "Promoted"),
                new StudentResultResponse.RankingInfo(1, 2, 100, true),
                new StudentResultResponse.AttendanceInfo(40, 38, 2, 95),
                null, // null grading scale
                List.of(),
                List.of(),
                "",
                ""
        );
        byte[] pdf1 = generator.generateStudentResultPdf(result1, "Test School");
        assertThat(pdf1).isNotEmpty();

        StudentResultResponse result2 = new StudentResultResponse(
                new StudentResultResponse.StudentInfo(UUID.randomUUID(), "STU123", "Test", "Class A", 1, null),
                new StudentResultResponse.TermInfo(UUID.randomUUID(), "First Term", "2025/2026"),
                List.of(subject("English Language", 20, 55, 75, "A", 1)),
                new StudentResultResponse.ResultSummary(BigDecimal.valueOf(75), 100, BigDecimal.valueOf(75), "A", BigDecimal.valueOf(1), 1, 1, 0, BigDecimal.valueOf(75), BigDecimal.valueOf(75), BigDecimal.valueOf(75), "Promoted"),
                new StudentResultResponse.RankingInfo(1, 2, 100, true),
                new StudentResultResponse.AttendanceInfo(40, 38, 2, 95),
                List.of(), // empty grading scale
                List.of(),
                List.of(),
                "",
                ""
        );
        byte[] pdf2 = generator.generateStudentResultPdf(result2, "Test School");
        assertThat(pdf2).isNotEmpty();
    }

    @Test
    void shouldHandleNullCommentsAndAssessments() throws Exception {
        StudentResultResponse result = new StudentResultResponse(
                new StudentResultResponse.StudentInfo(UUID.randomUUID(), "STU123", "Test", "Class A", 1, null),
                new StudentResultResponse.TermInfo(UUID.randomUUID(), "First Term", "2025/2026"),
                List.of(subject("English Language", 20, 55, 75, "A", 1)),
                new StudentResultResponse.ResultSummary(BigDecimal.valueOf(75), 100, BigDecimal.valueOf(75), "A", BigDecimal.valueOf(1), 1, 1, 0, BigDecimal.valueOf(75), BigDecimal.valueOf(75), BigDecimal.valueOf(75), "Promoted"),
                new StudentResultResponse.RankingInfo(1, 2, 100, true),
                new StudentResultResponse.AttendanceInfo(40, 38, 2, 95),
                List.of(new StudentResultResponse.GradeScaleItem("A", BigDecimal.valueOf(75), BigDecimal.valueOf(100), "Excellent")),
                null, // null behavioural
                null, // null psychomotor
                null, // null teacherComment
                null  // null principalComment
        );
        byte[] pdf = generator.generateStudentResultPdf(result, "Test School");
        assertThat(pdf).isNotEmpty();
    }

    @Test
    void shouldHandleOrdinalSuffixesAndPositionEdgeCases() throws Exception {
        // Test ordinal helper and trait comments
        // 11th, 12th, 13th, ending in 1, 2, 3, other
        int[] positions = {1, 2, 3, 4, 11, 12, 13, 21, 22, 23, 100};
        for (int pos : positions) {
            StudentResultResponse result = new StudentResultResponse(
                    new StudentResultResponse.StudentInfo(UUID.randomUUID(), "STU123", "Test", "Class A", 1, null),
                    new StudentResultResponse.TermInfo(UUID.randomUUID(), "First Term", "2025/2026"),
                    List.of(subject("English", 20, 50, 70, "B", pos)),
                    new StudentResultResponse.ResultSummary(BigDecimal.valueOf(70), 100, BigDecimal.valueOf(70), "B", BigDecimal.valueOf(pos), 1, 1, 0, BigDecimal.valueOf(70), BigDecimal.valueOf(70), BigDecimal.valueOf(70), "Promoted"),
                    new StudentResultResponse.RankingInfo(pos, 100, 100, true),
                    null,
                    null,
                    List.of(
                        new StudentResultResponse.TraitAssessment("Punctuality", "BEHAVIOURAL", "A", null), // null comment
                        new StudentResultResponse.TraitAssessment("Neatness", "BEHAVIOURAL", "B", "") // empty comment
                    ),
                    List.of(),
                    "Good",
                    "Approved"
            );
            byte[] pdf = generator.generateStudentResultPdf(result, "Test School");
            assertThat(pdf).isNotEmpty();
        }
    }

    private StudentResultResponse createBaseResult() {
        return new StudentResultResponse(
                new StudentResultResponse.StudentInfo(UUID.randomUUID(), "STU123", "Test", "Class A", 1, null),
                new StudentResultResponse.TermInfo(UUID.randomUUID(), "First Term", "2025/2026"),
                List.of(subject("English Language", 20, 55, 75, "A", 1)),
                new StudentResultResponse.ResultSummary(BigDecimal.valueOf(75), 100, BigDecimal.valueOf(75), "A", BigDecimal.valueOf(1), 1, 1, 0, BigDecimal.valueOf(75), BigDecimal.valueOf(75), BigDecimal.valueOf(75), "Promoted"),
                new StudentResultResponse.RankingInfo(1, 2, 100, true),
                new StudentResultResponse.AttendanceInfo(40, 38, 2, 95),
                List.of(new StudentResultResponse.GradeScaleItem("A", BigDecimal.valueOf(75), BigDecimal.valueOf(100), "Excellent")),
                List.of(),
                List.of(),
                "Good",
                "Approved"
        );
    }

    private StudentResultResponse.SubjectResult subject(
            String name, int ca, int exam, int total, String grade, int position) {
        return new StudentResultResponse.SubjectResult(
                UUID.randomUUID(),
                name,
                List.of(new StudentResultResponse.CaBreakdown(
                        "Continuous Assessment", BigDecimal.valueOf(ca), 40)),
                BigDecimal.valueOf(ca),
                40,
                BigDecimal.valueOf(exam),
                60,
                BigDecimal.valueOf(total),
                100,
                BigDecimal.valueOf(total),
                grade,
                total >= 50 ? "Pass" : "Fail",
                BigDecimal.valueOf(total >= 70 ? 5 : 3),
                position,
                BigDecimal.valueOf(75),
                BigDecimal.valueOf(55),
                BigDecimal.valueOf(65),
                UUID.randomUUID(),
                UUID.randomUUID());
    }
}
