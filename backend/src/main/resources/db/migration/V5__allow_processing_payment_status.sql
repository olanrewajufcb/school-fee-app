ALTER TABLE payment.payments
    DROP CONSTRAINT IF EXISTS payments_status_check;

ALTER TABLE payment.payments
    ADD CONSTRAINT payments_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'));
