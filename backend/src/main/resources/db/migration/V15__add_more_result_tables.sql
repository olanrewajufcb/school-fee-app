CREATE TYPE result.education_level AS ENUM (
  'NURSERY',
  'PRIMARY',
  'JUNIOR_SECONDARY',
  'SENIOR_SECONDARY'
);

ALTER TABLE school.classes
    ADD COLUMN education_level result.education_level;

CREATE TABLE result.report_card_templates (
                                              id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                              school_id UUID REFERENCES school.schools(id) NOT NULL,
                                              name VARCHAR(100) NOT NULL,
                                              education_level result.education_level NOT NULL,

                                              config JSONB NOT NULL,

                                              is_default BOOLEAN DEFAULT false,
                                              is_active BOOLEAN DEFAULT true,
                                              created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                              updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                              UNIQUE(school_id, education_level, name)
);

CREATE TABLE result.assessment_traits (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          school_id UUID REFERENCES school.schools(id) NOT NULL,
                                          education_level result.education_level,
                                          name VARCHAR(100) NOT NULL,
                                          category VARCHAR(30) NOT NULL CHECK (category IN ('AFFECTIVE', 'PSYCHOMOTOR', 'SKILL')),
                                          sort_order INT DEFAULT 0,
                                          is_active BOOLEAN DEFAULT true,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE result.student_trait_assessments (
                                                  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                  school_id UUID REFERENCES school.schools(id) NOT NULL,
                                                  student_id UUID REFERENCES school.students(id) NOT NULL,
                                                  class_id UUID REFERENCES school.classes(id) NOT NULL,
                                                  term_id UUID REFERENCES school.terms(id) NOT NULL,
                                                  trait_id UUID REFERENCES result.assessment_traits(id) NOT NULL,

                                                  rating VARCHAR(20) NOT NULL,
                                                  comment TEXT,
                                                  recorded_by UUID REFERENCES auth.users(id),

                                                  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                                  UNIQUE(student_id, term_id, trait_id)
);

CREATE TABLE result.student_health_records (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               school_id UUID REFERENCES school.schools(id) NOT NULL,
                                               student_id UUID REFERENCES school.students(id) NOT NULL,
                                               term_id UUID REFERENCES school.terms(id) NOT NULL,

                                               height_cm DECIMAL(5,2),
                                               weight_kg DECIMAL(5,2),
                                               health_comment TEXT,

                                               recorded_by UUID REFERENCES auth.users(id),
                                               created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                               UNIQUE(student_id, term_id)
);

ALTER TABLE result.report_comments
    ADD COLUMN promotion_status VARCHAR(30),
ADD COLUMN promotion_class_id UUID REFERENCES school.classes(id);

CREATE TABLE result.subject_group_rules (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                            school_id UUID REFERENCES school.schools(id) NOT NULL,
                                            education_level result.education_level NOT NULL,
                                            group_name VARCHAR(50) NOT NULL,
                                            min_subjects_required INT,
                                            is_required BOOLEAN DEFAULT false,
                                            created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE result.class_rankings
    ADD COLUMN subjects_credited INT DEFAULT 0,
ADD COLUMN subjects_failed INT DEFAULT 0;

ALTER TABLE result.ca_components
    ADD COLUMN education_level result.education_level,
ADD COLUMN class_id UUID REFERENCES school.classes(id);

CREATE TABLE result.comment_templates (
                                          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                          school_id UUID REFERENCES school.schools(id) NOT NULL,
                                          education_level result.education_level,
                                          comment_type VARCHAR(30) NOT NULL CHECK (comment_type IN ('TEACHER', 'PRINCIPAL', 'HEADMASTER')),
                                          min_average DECIMAL(5,2) NOT NULL,
                                          max_average DECIMAL(5,2) NOT NULL,
                                          attendance_min_percentage DECIMAL(5,2),
                                          behaviour_rating VARCHAR(20),
                                          template TEXT NOT NULL,
                                          is_active BOOLEAN DEFAULT true,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

ALTER TABLE result.report_comments
    ADD COLUMN teacher_comment_auto TEXT,
ADD COLUMN teacher_comment_final TEXT,
ADD COLUMN principal_comment_auto TEXT,
ADD COLUMN principal_comment_final TEXT,
ADD COLUMN comments_generated_at TIMESTAMP WITH TIME ZONE;