package com.fee.app.schoolfeeapp.fee.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record FeeDashboardResponse(
        String termName,
        DashboardSummary summary,
        List<ClassCollection> byClass,
        UpcomingDeadlines upcomingDeadlines,
        List<DailyTrend> dailyCollectionTrend
) {
    public record DashboardSummary(
            BigDecimal totalExpected,
            BigDecimal totalCollected,
            BigDecimal totalOutstanding,
            double collectionRate,
            int fullyPaidStudents,
            int partiallyPaidStudents,
            int unpaidStudents
    ) {}

    public record ClassCollection(
            String classId,
            String className,
            int studentCount,
            BigDecimal expectedAmount,
            BigDecimal collectedAmount,
            double collectionRate
    ) {}

    public record UpcomingDeadlines(
            DeadlineInfo dueIn3Days,
            DeadlineInfo dueToday,
            DeadlineInfo overdue
    ) {}

    public record DeadlineInfo(
            int count,
            BigDecimal amount
    ) {}

    public record DailyTrend(
            String date,
            BigDecimal amount,
            int transactions
    ) {}
}