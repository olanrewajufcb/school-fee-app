package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.List;

public record ClassResultSheetResponse(
        String className,
        String termName,
        int classSize,
        List<String> subjects,
        List<StudentRow> students
) {
    public record StudentRow(
            String studentId, String admissionNumber, String name,
            int position, double average, String overallGrade,
            List<SubjectScore> subjects
    ) {}
    public record SubjectScore(
            String subject, double finalScore, String grade, int position
    ) {}
}