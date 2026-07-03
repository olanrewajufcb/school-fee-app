-- Allow multi-fee payments where student breakdown is in payment_allocations
ALTER TABLE payment.payments
    ALTER COLUMN student_id DROP NOT NULL;

ALTER TABLE payment.payments
    ALTER COLUMN student_fee_id DROP NOT NULL;

-- Add status constraint if not already there
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'payments_status_check'
        AND conrelid = 'payment.payments'::regclass
    ) THEN
ALTER TABLE payment.payments
    ADD CONSTRAINT payments_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED', 'PENDING_VERIFICATION', 'REFUNDED'));
END IF;
END $$;