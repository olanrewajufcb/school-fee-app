-- ============================================================================
-- RESULTS SCHEMA (Complete — MVP with CA, Rankings, Ties, Audit, Publication)
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS result;

-- ============================================================================
-- SUBJECTS
-- ============================================================================
CREATE TABLE result.subjects (
                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 school_id UUID REFERENCES school.schools(id) NOT NULL,
                                 name VARCHAR(100) NOT NULL,
                                 code VARCHAR(20),
                                 category VARCHAR(50),        -- SCIENCE, ARTS, COMMERCIAL, LANGUAGES
                                 is_active BOOLEAN DEFAULT true,
                                 created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                 UNIQUE(school_id, name)
);

-- ============================================================================
-- CLASS-SUBJECT MAPPING
-- ============================================================================
CREATE TABLE result.class_subjects (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       school_id UUID REFERENCES school.schools(id) NOT NULL,
                                       class_id UUID REFERENCES school.classes(id) NOT NULL,
                                       subject_id UUID REFERENCES result.subjects(id) NOT NULL,
                                       teacher_id UUID REFERENCES auth.users(id),
                                       is_active BOOLEAN DEFAULT true,
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                       UNIQUE(class_id, subject_id)
);

-- ============================================================================
-- EXAMS (End-of-Term, Mid-Term, Mock)
-- ============================================================================
CREATE TABLE result.exams (
                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              school_id UUID REFERENCES school.schools(id) NOT NULL,
                              term_id UUID REFERENCES school.terms(id) NOT NULL,
                              name VARCHAR(200) NOT NULL,
                              exam_type VARCHAR(30) NOT NULL CHECK (exam_type IN ('END_OF_TERM', 'MID_TERM', 'MOCK')),
                              exam_date DATE,
                              max_score INT DEFAULT 100,
                              weight_percentage DECIMAL(5,2) DEFAULT 100.00,
                              is_published BOOLEAN DEFAULT false,
                              created_by UUID REFERENCES auth.users(id),
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- CONTINUOUS ASSESSMENT COMPONENTS
-- ============================================================================
CREATE TABLE result.ca_components (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      school_id UUID REFERENCES school.schools(id) NOT NULL,
                                      name VARCHAR(100) NOT NULL,         -- "First Test", "Second Test", "Homework"
                                      max_score INT DEFAULT 20,
                                      weight_percentage DECIMAL(5,2) DEFAULT 20.00,
                                      sort_order INT DEFAULT 0,
                                      is_active BOOLEAN DEFAULT true,
                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- CA SCORES (per student, per subject, per component)
-- ============================================================================
CREATE TABLE result.ca_scores (
                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                  school_id UUID REFERENCES school.schools(id) NOT NULL,
                                  student_id UUID REFERENCES school.students(id) NOT NULL,
                                  subject_id UUID REFERENCES result.subjects(id) NOT NULL,
                                  class_id UUID REFERENCES school.classes(id) NOT NULL,
                                  term_id UUID REFERENCES school.terms(id) NOT NULL,
                                  ca_component_id UUID REFERENCES result.ca_components(id) NOT NULL,
                                  score DECIMAL(5,1) NOT NULL CHECK (score >= 0),
                                  max_score INT DEFAULT 20,
                                  recorded_by UUID REFERENCES auth.users(id),
                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                  UNIQUE(student_id, subject_id, term_id, ca_component_id)
);

-- ============================================================================
-- EXAM SCORES
-- ============================================================================
CREATE TABLE result.scores (
                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               school_id UUID REFERENCES school.schools(id) NOT NULL,
                               exam_id UUID REFERENCES result.exams(id) NOT NULL,
                               student_id UUID REFERENCES school.students(id) NOT NULL,
                               subject_id UUID REFERENCES result.subjects(id) NOT NULL,
                               class_id UUID REFERENCES school.classes(id) NOT NULL,
                               term_id UUID REFERENCES school.terms(id) NOT NULL,
                               score DECIMAL(5,1) NOT NULL CHECK (score >= 0),
                               max_score INT DEFAULT 100,
                               grade VARCHAR(5),
                               remark VARCHAR(50),
                               points DECIMAL(3,1),
                               recorded_by UUID REFERENCES auth.users(id),
                               created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                               updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                               UNIQUE(exam_id, student_id, subject_id)
);

-- ============================================================================
-- FINAL SCORES (CA + Exam combined)
-- No: percentage, class_highest, class_lowest, class_average
-- Those are in the subject_statistics view
-- ============================================================================
CREATE TABLE result.final_scores (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                     school_id UUID REFERENCES school.schools(id) NOT NULL,
                                     student_id UUID REFERENCES school.students(id) NOT NULL,
                                     subject_id UUID REFERENCES result.subjects(id) NOT NULL,
                                     class_id UUID REFERENCES school.classes(id) NOT NULL,
                                     term_id UUID REFERENCES school.terms(id) NOT NULL,

    -- CA breakdown
                                     ca_total DECIMAL(5,1) DEFAULT 0,
                                     ca_max_total INT DEFAULT 0,

    -- Exam score
                                     exam_score DECIMAL(5,1) DEFAULT 0,
                                     exam_max_score INT DEFAULT 100,

    -- Computed final score (weighted out of 100)
                                     final_score DECIMAL(5,1) NOT NULL CHECK (final_score >= 0 AND final_score <= 100),

    -- Grade derived from grading config
                                     grade VARCHAR(5),
                                     remark VARCHAR(50),
                                     points DECIMAL(3,1),

    -- Position in class for this subject (uses RANK for ties)
                                     subject_position INT,

                                     computed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                     UNIQUE(student_id, subject_id, term_id)
);

-- ============================================================================
-- SUBJECT STATISTICS (View — replaces duplicated columns)
-- ============================================================================
CREATE VIEW result.subject_statistics AS
SELECT
    class_id,
    subject_id,
    term_id,
    school_id,
    MAX(final_score) AS highest,
    MIN(final_score) AS lowest,
    AVG(final_score)::DECIMAL(5,2) AS average,
    COUNT(*) AS students_assessed,
    COUNT(CASE WHEN final_score >= 50 THEN 1 END) AS students_passed
FROM result.final_scores
GROUP BY class_id, subject_id, term_id, school_id;

-- ============================================================================
-- CLASS RANKINGS (per student per term — uses RANK for ties)
-- ============================================================================
CREATE TABLE result.class_rankings (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       school_id UUID REFERENCES school.schools(id) NOT NULL,
                                       student_id UUID REFERENCES school.students(id) NOT NULL,
                                       class_id UUID REFERENCES school.classes(id) NOT NULL,
                                       term_id UUID REFERENCES school.terms(id) NOT NULL,

                                       total_score DECIMAL(7,1) NOT NULL,
                                       total_max_score INT NOT NULL,
                                       average_percentage DECIMAL(5,2) NOT NULL,
                                       overall_grade VARCHAR(5),

    -- Position uses RANK() — ties get same position
                                       class_position INT,
                                       out_of INT,

                                       subjects_taken INT,
                                       subjects_passed INT,

                                       computed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                       UNIQUE(student_id, term_id)
);

-- ============================================================================
-- GRADE CONFIGURATION (per school)
-- ============================================================================
CREATE TABLE result.grade_configs (
                                      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      school_id UUID REFERENCES school.schools(id) NOT NULL UNIQUE,
                                      config JSONB NOT NULL DEFAULT '{
                                        "grades": [
                                          {"grade": "A1", "minScore": 80, "maxScore": 100, "remark": "Excellent", "points": 4.0},
                                          {"grade": "B2", "minScore": 70, "maxScore": 79,  "remark": "Very Good",  "points": 3.5},
                                          {"grade": "B3", "minScore": 65, "maxScore": 69,  "remark": "Very Good",  "points": 3.0},
                                          {"grade": "C4", "minScore": 60, "maxScore": 64,  "remark": "Good",       "points": 2.5},
                                          {"grade": "C5", "minScore": 55, "maxScore": 59,  "remark": "Good",       "points": 2.0},
                                          {"grade": "C6", "minScore": 50, "maxScore": 54,  "remark": "Good",       "points": 1.5},
                                          {"grade": "D7", "minScore": 45, "maxScore": 49,  "remark": "Fair",       "points": 1.0},
                                          {"grade": "E8", "minScore": 40, "maxScore": 44,  "remark": "Poor",       "points": 0.5},
                                          {"grade": "F9", "minScore": 0,  "maxScore": 39,  "remark": "Fail",       "points": 0.0}
                                        ],
                                        "passMark": 40,
                                        "creditMark": 50,
                                        "usePoints": true,
                                        "roundingRule": "NEAREST_WHOLE"
                                      }'::jsonb,
                                      ca_config JSONB,
                                      is_active BOOLEAN DEFAULT true,
                                      created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                      updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- ============================================================================
-- REPORT CARD COMMENTS
-- ============================================================================
CREATE TABLE result.report_comments (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        school_id UUID REFERENCES school.schools(id) NOT NULL,
                                        student_id UUID REFERENCES school.students(id) NOT NULL,
                                        term_id UUID REFERENCES school.terms(id) NOT NULL,

                                        teacher_comment TEXT,
                                        teacher_id UUID REFERENCES auth.users(id),

                                        principal_comment TEXT,
                                        principal_id UUID REFERENCES auth.users(id),

                                        attendance_days_open INT,
                                        attendance_days_present INT,
                                        attendance_days_absent INT,

                                        next_term_resumes DATE,
                                        next_term_fees DECIMAL(10,2),

                                        created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                        updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                        UNIQUE(student_id, term_id)
);

-- ============================================================================
-- RESULT PUBLICATION (Freezes scores, gates parent access)
-- ============================================================================
CREATE TABLE result.published_results (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          school_id UUID REFERENCES school.schools(id) NOT NULL,
                                          term_id UUID REFERENCES school.terms(id) NOT NULL,
                                          published_by UUID REFERENCES auth.users(id) NOT NULL,
                                          published_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                          UNIQUE(school_id, term_id)
);

-- ============================================================================
-- SCORE AUDIT LOG (Immutable change history)
-- ============================================================================
CREATE TABLE result.score_audit_log (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        school_id UUID REFERENCES school.schools(id) NOT NULL,
                                        score_type VARCHAR(20) NOT NULL CHECK (score_type IN ('CA_SCORE', 'EXAM_SCORE')),
                                        score_id UUID NOT NULL,
                                        student_id UUID REFERENCES school.students(id) NOT NULL,
                                        subject_id UUID REFERENCES result.subjects(id) NOT NULL,
                                        term_id UUID REFERENCES school.terms(id) NOT NULL,
                                        old_score DECIMAL(5,1),
                                        new_score DECIMAL(5,1) NOT NULL,
                                        changed_by UUID REFERENCES auth.users(id) NOT NULL,
                                        changed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                        reason TEXT
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Scores
CREATE INDEX idx_scores_student ON result.scores(student_id);
CREATE INDEX idx_scores_exam ON result.scores(exam_id);
CREATE INDEX idx_scores_class_subject ON result.scores(class_id, subject_id);
CREATE INDEX idx_scores_term ON result.scores(term_id);

-- CA Scores
CREATE INDEX idx_ca_scores_student ON result.ca_scores(student_id);
CREATE INDEX idx_ca_scores_term ON result.ca_scores(term_id);

-- Final Scores
CREATE INDEX idx_final_scores_student ON result.final_scores(student_id);
CREATE INDEX idx_final_scores_class_term ON result.final_scores(class_id, term_id);
CREATE INDEX idx_final_scores_subject ON result.final_scores(subject_id);
CREATE INDEX idx_final_scores_position ON result.final_scores(class_id, term_id, subject_id, subject_position);

-- Class Rankings
CREATE INDEX idx_class_rankings_class_term ON result.class_rankings(class_id, term_id);
CREATE INDEX idx_class_rankings_student ON result.class_rankings(student_id);
CREATE INDEX idx_class_rankings_position ON result.class_rankings(class_id, term_id, class_position);

-- Class Subjects
CREATE INDEX idx_class_subjects_class ON result.class_subjects(class_id);
CREATE INDEX idx_class_subjects_teacher ON result.class_subjects(teacher_id);

-- Audit Log
CREATE INDEX idx_audit_score_id ON result.score_audit_log(score_type, score_id);
CREATE INDEX idx_audit_term ON result.score_audit_log(term_id);
CREATE INDEX idx_audit_changed_by ON result.score_audit_log(changed_by);

-- Report Comments
CREATE INDEX idx_report_comments_student ON result.report_comments(student_id, term_id);

-- Published Results
CREATE INDEX idx_published_results_term ON result.published_results(term_id);

-- ============================================================================
-- COMMENTS
-- ============================================================================
COMMENT ON TABLE result.final_scores IS 'Computed from CA + Exam scores. final_score is weighted out of 100. Statistics in subject_statistics view.';
COMMENT ON VIEW result.subject_statistics IS 'Per-class per-subject aggregates. Joins with final_scores. Replaces duplicated columns.';
COMMENT ON TABLE result.class_rankings IS 'Uses RANK() for ties — students with same average get same position, next position skips.';
COMMENT ON TABLE result.published_results IS 'Presence of a row = results published. Deletion = unpublished. Gates parent access.';
COMMENT ON TABLE result.score_audit_log IS 'Immutable. Every score change creates a row. Never deleted.';
COMMENT ON TABLE result.grade_configs IS 'Per-school grading rules. Default: Nigerian 9-point scale (A1-F9).';
