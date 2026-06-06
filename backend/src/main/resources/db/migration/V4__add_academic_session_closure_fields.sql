ALTER TABLE school.academic_sessions
    ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE',
    ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    ADD COLUMN closed_by UUID DEFAULT NULL,
    ADD COLUMN closed_notes TEXT DEFAULT NULL;

ALTER TABLE school.academic_sessions
    ADD CONSTRAINT chk_academic_sessions_status
        CHECK (status IS NULL OR status IN ('ACTIVE', 'COMPLETED'));

CREATE INDEX idx_academic_sessions_school_status
    ON school.academic_sessions(school_id, status);

ALTER TABLE school.terms
    ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE',
    ADD COLUMN completed_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    ADD COLUMN completed_by UUID DEFAULT NULL;

ALTER TABLE school.terms
    ADD CONSTRAINT chk_terms_status
        CHECK (status IS NULL OR status IN ('ACTIVE', 'COMPLETED'));

CREATE INDEX idx_terms_session_status
    ON school.terms(session_id, status);
