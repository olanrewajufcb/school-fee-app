CREATE TABLE IF NOT EXISTS notification.reminder_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id UUID NOT NULL REFERENCES school.schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(50) NOT NULL,
    template_code VARCHAR(50) NOT NULL,
    days_offset INT NOT NULL DEFAULT 0,
    send_time TIME NOT NULL,
    recipient_role VARCHAR(50) NOT NULL DEFAULT 'GUARDIAN',
    is_recurring BOOLEAN NOT NULL DEFAULT false,
    recurring_interval_days INT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT reminder_schedules_recurring_interval_positive
        CHECK (recurring_interval_days IS NULL OR recurring_interval_days > 0)
);

CREATE INDEX IF NOT EXISTS idx_reminder_schedules_school
    ON notification.reminder_schedules(school_id);

CREATE INDEX IF NOT EXISTS idx_reminder_schedules_school_active
    ON notification.reminder_schedules(school_id, is_active);
