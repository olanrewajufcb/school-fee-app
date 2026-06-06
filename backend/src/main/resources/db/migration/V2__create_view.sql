
-- ledger_entries.amount: Positive = debit (FEE, LATE_FEE), Negative = credit (PAYMENT, DISCOUNT)
CREATE MATERIALIZED VIEW fee.student_fee_balance AS
SELECT
    sf.id AS student_fee_id,
    sf.student_id,
    sf.school_id,
    sf.fee_structure_id,
    sf.due_date,

    sf.total_amount AS original_amount,
    sf.discount_amount AS total_discounts,

    COALESCE(SUM(le.amount), 0) AS current_balance,

    COALESCE(
            SUM(
                    CASE
                        WHEN le.entry_type = 'PAYMENT'
                            THEN ABS(le.amount)
                        ELSE 0
                        END
            ),
            0
    ) AS total_paid,

    CASE
        WHEN COALESCE(SUM(le.amount), 0) <= 0 THEN 'PAID'

        WHEN EXISTS (
            SELECT 1
            FROM fee.ledger_entries le2
            WHERE le2.student_fee_id = sf.id
              AND le2.entry_type = 'PAYMENT'
              AND le2.is_reversed = false
              AND le2.deleted_at IS NULL
        ) THEN 'PARTIAL'

        WHEN sf.due_date < CURRENT_DATE THEN 'OVERDUE'

        ELSE 'PENDING'
        END AS computed_status,

    COALESCE(
            SUM(
                    CASE
                        WHEN le.entry_type = 'LATE_FEE_APPLIED'
                            THEN le.amount
                        ELSE 0
                        END
            ),
            0
    ) AS total_late_fees,

    MAX(le.created_at) AS last_transaction_at,

    NOW() AS computed_at

FROM fee.student_fees sf
         LEFT JOIN fee.ledger_entries le
                   ON sf.id = le.student_fee_id
                       AND le.is_reversed = false
                       AND le.deleted_at IS NULL

GROUP BY
    sf.id,
    sf.student_id,
    sf.school_id,
    sf.fee_structure_id,
    sf.total_amount,
    sf.discount_amount,
    sf.due_date;


CREATE UNIQUE INDEX idx_student_fee_balance_id ON fee.student_fee_balance(student_fee_id);
CREATE INDEX idx_student_fee_balance_student ON fee.student_fee_balance(student_id);
CREATE INDEX idx_student_fee_balance_school_status ON fee.student_fee_balance(school_id, computed_status);
CREATE INDEX idx_student_fee_balance_due_date ON fee.student_fee_balance(due_date, computed_status);
-- Refresh as needed
-- for reporting
CREATE MATERIALIZED VIEW notification.notification_stats AS
SELECT
    school_id,
    channel,
    status,
    COUNT(*) AS total,
    SUM(CASE WHEN status = 'DELIVERED' THEN 1 ELSE 0 END) AS delivered,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
    NOW() AS last_computed
FROM notification.notifications
WHERE deleted_at IS NULL
GROUP BY school_id, channel, status;

-- Refresh as needed

