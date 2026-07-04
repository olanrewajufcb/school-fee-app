ALTER TABLE result.report_card_templates
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uq_report_card_templates_one_active_default
    ON result.report_card_templates (school_id, education_level)
    WHERE is_active = true AND is_default = true;
