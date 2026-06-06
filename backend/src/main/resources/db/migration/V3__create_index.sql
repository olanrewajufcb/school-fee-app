-- Users
CREATE INDEX idx_users_keycloak ON auth.users(keycloak_id);
CREATE UNIQUE INDEX idx_users_sch_phone ON auth.users(school_id, phone)
WHERE deleted_at IS NULL;
CREATE INDEX idx_users_deleted_at ON auth.users(deleted_at);
CREATE UNIQUE INDEX uq_users_school_email ON auth.users(school_id, email) WHERE deleted_at IS NULL;

-- Students
CREATE INDEX idx_students_school ON school.students(school_id);
CREATE INDEX idx_students_admission ON school.students(admission_number);
CREATE INDEX idx_students_deleted_at ON school.students(deleted_at);

-- Student Fees
CREATE INDEX idx_student_fees_student ON fee.student_fees(student_id);
-- Note: Status is derived from fee.student_fee_balance materialized view, not stored in table
CREATE INDEX idx_student_fees_due_date ON fee.student_fees(due_date);
CREATE INDEX idx_student_fees_school ON fee.student_fees(school_id);

-- Payments
CREATE INDEX idx_payments_student ON payment.payments(student_id);
CREATE INDEX idx_payments_school_status ON payment.payments(school_id, status);
CREATE INDEX idx_payments_gateway_ref ON payment.payments(gateway_transaction_ref);
CREATE INDEX idx_payments_deleted_at ON payment.payments(deleted_at);
CREATE INDEX idx_payments_status_created
    ON payment.payments(status, created_at);

-- Ledger Entries

CREATE INDEX idx_ledger_student_fee ON fee.ledger_entries(student_fee_id)  WHERE deleted_at IS NULL;
CREATE INDEX idx_ledger_student ON fee.ledger_entries(student_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ledger_school ON fee.ledger_entries(school_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_ledger_source ON fee.ledger_entries(source_entity_type, source_entity_id) WHERE deleted_at IS NULL;
-- Note: idx_ledger_idempotency is created in the Idempotency section below
CREATE INDEX idx_ledger_transaction_date
    ON fee.ledger_entries(transaction_date);

-- Notifications
CREATE INDEX idx_notifications_status ON notification.notifications(status);
CREATE INDEX idx_notifications_school_status ON notification.notifications(school_id, status);
CREATE INDEX idx_notifications_deleted_at ON notification.notifications(deleted_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_notifications_retry
    ON notification.notifications(status, next_retry_at)
    WHERE status IN ('FAILED', 'RETRY');

-- Idempotency
CREATE UNIQUE INDEX idx_payments_idempotency ON payment.payments(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX idx_ledger_idempotency ON fee.ledger_entries(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX idx_notifications_idempotency ON notification.notifications(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- For querying all fee structures for a given class
CREATE INDEX idx_fee_structure_classes_class ON fee.fee_structure_classes(class_id);

-- For querying all classes under a fee structure
CREATE INDEX idx_fee_structure_classes_structure ON fee.fee_structure_classes(fee_structure_id);

-- For time-based filtering
CREATE INDEX idx_fee_structure_classes_effective ON fee.fee_structure_classes(effective_date);

-- outbox event table
CREATE INDEX idx_outbox_status_retry ON outbox.outbox_events(status, next_retry_at);
CREATE INDEX idx_outbox_created ON outbox.outbox_events(created_at);
-- Index for aggregate lookups (idempotency checks)
CREATE INDEX idx_outbox_events_aggregate ON outbox.outbox_events(aggregate_id);

-- Note: Materialized view indexes are created in V2__create_view.sql
-- payment allocations
CREATE INDEX idx_payment_allocations_payment ON payment.payment_allocations(payment_id);
CREATE INDEX idx_payment_allocations_fee ON payment.payment_allocations(student_fee_id);

-- Indexes
CREATE INDEX idx_guardians_school ON school.student_guardians(school_id);
CREATE INDEX idx_guardians_phone ON school.student_guardians(phone);
CREATE INDEX idx_guardian_links_student ON school.student_guardian_links(student_id);
CREATE INDEX idx_guardian_links_guardian ON school.student_guardian_links(guardian_id);
CREATE INDEX idx_guardian_links_primary ON school.student_guardian_links(student_id)
    WHERE is_primary_contact = true;

CREATE UNIQUE INDEX idx_guardians_unique ON school.student_guardians(school_id, phone)
WHERE deleted_at IS NULL;

CREATE INDEX idx_guardians_user ON school.student_guardians(user_id) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX idx_guardian_links_unique ON school.student_guardian_links(student_id, guardian_id)
WHERE deleted_at IS NULL;

CREATE INDEX idx_guardian_phone_school_unlinked
    ON school.student_guardians(phone, school_id)
    WHERE user_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX idx_guardian_user_school_unique
    ON school.student_guardians(user_id, school_id)
    WHERE deleted_at IS NULL AND user_id IS NOT NULL;


