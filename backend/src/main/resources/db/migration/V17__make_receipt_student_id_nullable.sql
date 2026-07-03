-- Drop NOT NULL constraint on student_id in payment.receipts
-- to allow receipts for multi-student/multi-fee transactions
ALTER TABLE payment.receipts ALTER COLUMN student_id DROP NOT NULL;
