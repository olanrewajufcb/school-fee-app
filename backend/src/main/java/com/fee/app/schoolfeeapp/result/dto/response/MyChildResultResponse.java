package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.List;
import java.util.UUID;

public record MyChildResultResponse(
        UUID studentId,
        UUID termId,
        String firstName,
        String lastName,
        String className,
        String termName,
        ResultSummary summary,
        List<TopSubject> topSubjects,
        AttendanceSummary attendance
) {
    public MyChildResultResponse(UUID studentId, UUID termId, String firstName, String lastName, String className, String termName, ResultSummary summary, List<TopSubject> topSubjects) {
        this(studentId, termId, firstName, lastName, className, termName, summary, topSubjects, null);
    }

    public record ResultSummary(double average, int totalSubjects, String grade) {}
    public record TopSubject(String name, double score, String grade) {}
    public record AttendanceSummary(int daysOpen, int daysPresent, int daysAbsent, double attendanceRate) {}
}