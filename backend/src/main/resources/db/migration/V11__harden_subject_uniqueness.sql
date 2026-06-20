ALTER TABLE result.subjects
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE result.subjects
    DROP CONSTRAINT IF EXISTS subjects_school_id_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subjects_school_normalized_name
    ON result.subjects (school_id, LOWER(BTRIM(name)));

CREATE UNIQUE INDEX IF NOT EXISTS uq_subjects_school_normalized_code
    ON result.subjects (school_id, LOWER(BTRIM(code)))
    WHERE code IS NOT NULL AND BTRIM(code) <> '';
