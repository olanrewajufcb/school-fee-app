package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.List;
import java.util.UUID;

public record MyChildResultResponse(
        UUID studentId,
        String firstName,
        String lastName,
        String className,
        String termName,
        ResultSummary summary,
        List<TopSubject> topSubjects
) {
    public record ResultSummary(double average, int totalSubjects, String grade) {}
    public record TopSubject(String name, double score, String grade) {}
}