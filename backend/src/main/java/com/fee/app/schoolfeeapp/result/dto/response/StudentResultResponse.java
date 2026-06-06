package com.fee.app.schoolfeeapp.result.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudentResultResponse(
        StudentInfo student,
        TermInfo term,
        List<SubjectResult> subjects,
        ResultSummary summary,
        RankingInfo ranking,
        AttendanceInfo attendance,
        String teacherComment,
        String principalComment
) {
    public record StudentInfo(
            UUID studentId, String admissionNumber, String fullName,
            String className, int classSize, String profilePhotoUrl
    ) {}
    public record TermInfo(UUID termId, String name, String sessionName) {}
    public record SubjectResult(
            UUID subjectId, String subjectName,
            List<CaBreakdown> caScores, BigDecimal caTotal, int caMaxTotal,
            BigDecimal examScore, int examMaxScore,
            BigDecimal finalScore, int finalMaxScore,
            BigDecimal percentage, String grade, String remark,
            BigDecimal points, int subjectPosition,
            BigDecimal classHighest, BigDecimal classLowest, BigDecimal classAverage
    ) {}
    public record CaBreakdown(String component, BigDecimal score, int maxScore) {}
    public record ResultSummary(
            BigDecimal totalScore, int totalMaxScore, BigDecimal average,
            String overallGrade, BigDecimal totalPoints,
            int subjectsTaken, int subjectsPassed, int subjectsFailed
    ) {}
    public record RankingInfo(int classPosition, int outOf, double percentile, boolean topThird) {}
    public record AttendanceInfo(int daysOpen, int daysPresent, int daysAbsent, double attendanceRate) {}
}