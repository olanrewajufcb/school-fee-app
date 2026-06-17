package com.fee.app.schoolfeeapp.fee.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class FeeReportingRepository {

    private final DatabaseClient databaseClient;

    public Mono<CollectionStats> getStructureCollectionStats(UUID schoolId, UUID structureId) {
        return databaseClient.sql("""
                WITH payment_totals AS (
                    SELECT
                        le.student_fee_id,
                        COALESCE(SUM(ABS(le.amount)), 0)::numeric AS collected
                    FROM fee.ledger_entries le
                    JOIN fee.student_fees sf ON sf.id = le.student_fee_id
                    WHERE sf.school_id = :schoolId
                      AND sf.fee_structure_id = :structureId
                      AND le.entry_type = 'PAYMENT'
                      AND COALESCE(le.is_reversed, false) = false
                      AND le.deleted_at IS NULL
                    GROUP BY le.student_fee_id
                ),
                balances AS (
                    SELECT
                        sf.id AS student_fee_id,
                        GREATEST(
                            sf.total_amount - COALESCE(sf.discount_amount, 0) + COALESCE(sf.late_fee_amount, 0),
                            0::numeric
                        ) AS expected,
                        COALESCE(pt.collected, 0)::numeric AS collected
                    FROM fee.student_fees sf
                    LEFT JOIN payment_totals pt ON pt.student_fee_id = sf.id
                    WHERE sf.school_id = :schoolId
                      AND sf.fee_structure_id = :structureId
                )
                SELECT
                    COUNT(*)::int AS student_count,
                    COALESCE(SUM(expected), 0)::numeric AS expected_amount,
                    COALESCE(SUM(collected), 0)::numeric AS collected_amount
                FROM balances
                """)
                .bind("schoolId", schoolId)
                .bind("structureId", structureId)
                .map((row, metadata) -> new CollectionStats(
                        money(row.get("expected_amount")),
                        money(row.get("collected_amount")),
                        number(row.get("student_count")).intValue()))
                .one()
                .defaultIfEmpty(CollectionStats.empty());
    }

    public Mono<DashboardSummaryStats> getDashboardSummary(UUID schoolId, UUID termId) {
        return databaseClient.sql("""
                WITH payment_totals AS (
                    SELECT
                        le.student_fee_id,
                        COALESCE(SUM(ABS(le.amount)), 0)::numeric AS collected
                    FROM fee.ledger_entries le
                    JOIN fee.student_fees sf ON sf.id = le.student_fee_id
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                      AND le.entry_type = 'PAYMENT'
                      AND COALESCE(le.is_reversed, false) = false
                      AND le.deleted_at IS NULL
                    GROUP BY le.student_fee_id
                ),
                balances AS (
                    SELECT
                        sf.id AS student_fee_id,
                        GREATEST(
                            sf.total_amount - COALESCE(sf.discount_amount, 0) + COALESCE(sf.late_fee_amount, 0),
                            0::numeric
                        ) AS expected,
                        COALESCE(pt.collected, 0)::numeric AS collected
                    FROM fee.student_fees sf
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    LEFT JOIN payment_totals pt ON pt.student_fee_id = sf.id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                )
                SELECT
                    COALESCE(SUM(expected), 0)::numeric AS total_expected,
                    COALESCE(SUM(collected), 0)::numeric AS total_collected,
                    COALESCE(SUM(GREATEST(expected - collected, 0::numeric)), 0)::numeric AS total_outstanding,
                    COALESCE(SUM(CASE WHEN GREATEST(expected - collected, 0::numeric) <= 0 AND expected > 0 THEN 1 ELSE 0 END), 0)::int AS fully_paid_students,
                    COALESCE(SUM(CASE WHEN collected > 0 AND GREATEST(expected - collected, 0::numeric) > 0 THEN 1 ELSE 0 END), 0)::int AS partially_paid_students,
                    COALESCE(SUM(CASE WHEN collected <= 0 AND GREATEST(expected - collected, 0::numeric) > 0 THEN 1 ELSE 0 END), 0)::int AS unpaid_students
                FROM balances
                """)
                .bind("schoolId", schoolId)
                .bind("termId", termId)
                .map((row, metadata) -> new DashboardSummaryStats(
                        money(row.get("total_expected")),
                        money(row.get("total_collected")),
                        money(row.get("total_outstanding")),
                        number(row.get("fully_paid_students")).intValue(),
                        number(row.get("partially_paid_students")).intValue(),
                        number(row.get("unpaid_students")).intValue()))
                .one()
                .defaultIfEmpty(DashboardSummaryStats.empty());
    }

    public Flux<ClassCollectionStats> getClassCollections(UUID schoolId, UUID termId) {
        return databaseClient.sql("""
                WITH payment_totals AS (
                    SELECT
                        le.student_fee_id,
                        COALESCE(SUM(ABS(le.amount)), 0)::numeric AS collected
                    FROM fee.ledger_entries le
                    JOIN fee.student_fees sf ON sf.id = le.student_fee_id
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                      AND le.entry_type = 'PAYMENT'
                      AND COALESCE(le.is_reversed, false) = false
                      AND le.deleted_at IS NULL
                    GROUP BY le.student_fee_id
                ),
                balances AS (
                    SELECT
                        sf.id AS student_fee_id,
                        sf.student_id,
                        GREATEST(
                            sf.total_amount - COALESCE(sf.discount_amount, 0) + COALESCE(sf.late_fee_amount, 0),
                            0::numeric
                        ) AS expected,
                        COALESCE(pt.collected, 0)::numeric AS collected
                    FROM fee.student_fees sf
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    LEFT JOIN payment_totals pt ON pt.student_fee_id = sf.id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                )
                SELECT
                    c.id::text AS class_id,
                    c.name AS class_name,
                    COUNT(b.student_fee_id)::int AS student_count,
                    COALESCE(SUM(b.expected), 0)::numeric AS expected_amount,
                    COALESCE(SUM(b.collected), 0)::numeric AS collected_amount
                FROM balances b
                JOIN school.students s ON s.id = b.student_id AND s.deleted_at IS NULL
                JOIN school.classes c ON c.id = s.current_class_id AND c.school_id = :schoolId
                GROUP BY c.id, c.name
                ORDER BY c.name ASC
                """)
                .bind("schoolId", schoolId)
                .bind("termId", termId)
                .map((row, metadata) -> new ClassCollectionStats(
                        (String) row.get("class_id"),
                        (String) row.get("class_name"),
                        number(row.get("student_count")).intValue(),
                        money(row.get("expected_amount")),
                        money(row.get("collected_amount"))))
                .all();
    }

    public Mono<DeadlineStats> getDeadlineStats(UUID schoolId, UUID termId, LocalDate today) {
        return databaseClient.sql("""
                WITH payment_totals AS (
                    SELECT
                        le.student_fee_id,
                        COALESCE(SUM(ABS(le.amount)), 0)::numeric AS collected
                    FROM fee.ledger_entries le
                    JOIN fee.student_fees sf ON sf.id = le.student_fee_id
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                      AND le.entry_type = 'PAYMENT'
                      AND COALESCE(le.is_reversed, false) = false
                      AND le.deleted_at IS NULL
                    GROUP BY le.student_fee_id
                ),
                balances AS (
                    SELECT
                        sf.due_date,
                        GREATEST(
                            GREATEST(
                                sf.total_amount - COALESCE(sf.discount_amount, 0) + COALESCE(sf.late_fee_amount, 0),
                                0::numeric
                            ) - COALESCE(pt.collected, 0),
                            0::numeric
                        ) AS outstanding
                    FROM fee.student_fees sf
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    LEFT JOIN payment_totals pt ON pt.student_fee_id = sf.id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                )
                SELECT
                    COUNT(*) FILTER (WHERE due_date = :dueInThreeDays AND outstanding > 0)::int AS due_in_three_days_count,
                    COALESCE(SUM(outstanding) FILTER (WHERE due_date = :dueInThreeDays AND outstanding > 0), 0)::numeric AS due_in_three_days_amount,
                    COUNT(*) FILTER (WHERE due_date = :today AND outstanding > 0)::int AS due_today_count,
                    COALESCE(SUM(outstanding) FILTER (WHERE due_date = :today AND outstanding > 0), 0)::numeric AS due_today_amount,
                    COUNT(*) FILTER (WHERE due_date < :today AND outstanding > 0)::int AS overdue_count,
                    COALESCE(SUM(outstanding) FILTER (WHERE due_date < :today AND outstanding > 0), 0)::numeric AS overdue_amount
                FROM balances
                """)
                .bind("schoolId", schoolId)
                .bind("termId", termId)
                .bind("today", today)
                .bind("dueInThreeDays", today.plusDays(3))
                .map((row, metadata) -> new DeadlineStats(
                        number(row.get("due_in_three_days_count")).intValue(),
                        money(row.get("due_in_three_days_amount")),
                        number(row.get("due_today_count")).intValue(),
                        money(row.get("due_today_amount")),
                        number(row.get("overdue_count")).intValue(),
                        money(row.get("overdue_amount"))))
                .one()
                .defaultIfEmpty(DeadlineStats.empty());
    }

    public Flux<DailyCollectionStats> getDailyCollectionTrend(
            UUID schoolId, UUID termId, LocalDate fromDate, LocalDate toDate) {
        return databaseClient.sql("""
                SELECT
                    TO_CHAR((le.transaction_date AT TIME ZONE 'UTC')::date, 'YYYY-MM-DD') AS collection_date,
                    COALESCE(SUM(ABS(le.amount)), 0)::numeric AS amount,
                    COUNT(*)::int AS transactions
                FROM fee.ledger_entries le
                JOIN fee.student_fees sf ON sf.id = le.student_fee_id
                JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                WHERE sf.school_id = :schoolId
                  AND fs.school_id = :schoolId
                  AND fs.term_id = :termId
                  AND fs.deleted_at IS NULL
                  AND le.entry_type = 'PAYMENT'
                  AND COALESCE(le.is_reversed, false) = false
                  AND le.deleted_at IS NULL
                  AND (le.transaction_date AT TIME ZONE 'UTC')::date BETWEEN :fromDate AND :toDate
                GROUP BY (le.transaction_date AT TIME ZONE 'UTC')::date
                ORDER BY (le.transaction_date AT TIME ZONE 'UTC')::date ASC
                """)
                .bind("schoolId", schoolId)
                .bind("termId", termId)
                .bind("fromDate", fromDate)
                .bind("toDate", toDate)
                .map((row, metadata) -> new DailyCollectionStats(
                        (String) row.get("collection_date"),
                        money(row.get("amount")),
                        number(row.get("transactions")).intValue()))
                .all();
    }

    private static BigDecimal money(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return 0;
    }

    public record CollectionStats(
            BigDecimal expectedAmount,
            BigDecimal collectedAmount,
            int studentCount) {

        static CollectionStats empty() {
            return new CollectionStats(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }
    }

    public record DashboardSummaryStats(
            BigDecimal totalExpected,
            BigDecimal totalCollected,
            BigDecimal totalOutstanding,
            int fullyPaidStudents,
            int partiallyPaidStudents,
            int unpaidStudents) {

        static DashboardSummaryStats empty() {
            return new DashboardSummaryStats(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    0,
                    0,
                    0);
        }
    }

    public record ClassCollectionStats(
            String classId,
            String className,
            int studentCount,
            BigDecimal expectedAmount,
            BigDecimal collectedAmount) {
    }

    public record DeadlineStats(
            int dueInThreeDaysCount,
            BigDecimal dueInThreeDaysAmount,
            int dueTodayCount,
            BigDecimal dueTodayAmount,
            int overdueCount,
            BigDecimal overdueAmount) {

        static DeadlineStats empty() {
            return new DeadlineStats(
                    0,
                    BigDecimal.ZERO,
                    0,
                    BigDecimal.ZERO,
                    0,
                    BigDecimal.ZERO);
        }
    }

    public record DailyCollectionStats(
            String date,
            BigDecimal amount,
            int transactions) {
    }

    public Flux<UUID> getOutstandingFeeIds(UUID schoolId, UUID termId, String filter, LocalDate today) {
        String deadlineCondition = switch (filter.toUpperCase()) {
            case "DUE_IN_3_DAYS" -> "sf.due_date = :dueInThreeDays";
            case "DUE_TODAY" -> "sf.due_date = :today";
            case "OVERDUE" -> "sf.due_date < :today";
            default -> "1=1";
        };

        return databaseClient.sql(String.format("""
                WITH payment_totals AS (
                    SELECT
                        le.student_fee_id,
                        COALESCE(SUM(ABS(le.amount)), 0)::numeric AS collected
                    FROM fee.ledger_entries le
                    JOIN fee.student_fees sf ON sf.id = le.student_fee_id
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                      AND le.entry_type = 'PAYMENT'
                      AND COALESCE(le.is_reversed, false) = false
                      AND le.deleted_at IS NULL
                    GROUP BY le.student_fee_id
                ),
                balances AS (
                    SELECT
                        sf.id AS student_fee_id,
                        sf.due_date,
                        GREATEST(
                            GREATEST(
                                sf.total_amount - COALESCE(sf.discount_amount, 0) + COALESCE(sf.late_fee_amount, 0),
                                0::numeric
                            ) - COALESCE(pt.collected, 0),
                            0::numeric
                        ) AS outstanding
                    FROM fee.student_fees sf
                    JOIN fee.fee_structures fs ON fs.id = sf.fee_structure_id
                    LEFT JOIN payment_totals pt ON pt.student_fee_id = sf.id
                    WHERE sf.school_id = :schoolId
                      AND fs.school_id = :schoolId
                      AND fs.term_id = :termId
                      AND fs.deleted_at IS NULL
                )
                SELECT student_fee_id
                FROM balances sf
                WHERE outstanding > 0
                  AND %s
                """, deadlineCondition))
                .bind("schoolId", schoolId)
                .bind("termId", termId)
                .bind("today", today)
                .bind("dueInThreeDays", today.plusDays(3))
                .map((row, metadata) -> row.get("student_fee_id", UUID.class))
                .all();
    }
}
