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
                        "B", BigDecimal.valueOf(8), 2, 2, 0),
                new StudentResultResponse.RankingInfo(1, 2, 100, true),
                new StudentResultResponse.AttendanceInfo(40, 38, 2, 95),
                "A focused and diligent learner.",
                "Excellent progress. Keep it up.");

        byte[] pdf = generator.generateStudentResultPdf(result, "Success School");

        assertThat(pdf).startsWith("%PDF".getBytes());
        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
        }

        String previewPath = System.getenv("PDF_PREVIEW_PATH");
        if (previewPath != null && !previewPath.isBlank()) {
            Files.write(Path.of(previewPath), pdf);
        }
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
                BigDecimal.valueOf(65));
    }
}
