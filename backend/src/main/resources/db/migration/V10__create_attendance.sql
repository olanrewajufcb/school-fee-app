-- ============================================================================
-- ATTENDANCE SCHEMA
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS attendance;

-- Daily attendance sessions (morning arrival + afternoon departure)
CREATE TABLE attendance.attendance_sessions (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                                school_id UUID REFERENCES school.schools(id) NOT NULL,
                                                class_id UUID REFERENCES school.classes(id) NOT NULL,
                                                term_id UUID REFERENCES school.terms(id) NOT NULL,
                                                date DATE NOT NULL,
                                                session_type VARCHAR(20) NOT NULL CHECK (session_type IN ('MORNING_ARRIVAL', 'AFTERNOON_DEPARTURE')),
                                                marked_by UUID REFERENCES auth.users(id),
                                                marked_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                                is_complete BOOLEAN DEFAULT false,
                                                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                                UNIQUE(class_id, date, session_type)
);

-- Individual student attendance records
CREATE TABLE attendance.student_attendance (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                               school_id UUID REFERENCES school.schools(id) NOT NULL,
                                               session_id UUID REFERENCES attendance.attendance_sessions(id) NOT NULL,
                                               student_id UUID REFERENCES school.students(id) NOT NULL,
                                               class_id UUID REFERENCES school.classes(id) NOT NULL,
                                               term_id UUID REFERENCES school.terms(id) NOT NULL,
                                               date DATE NOT NULL,

    -- Status
                                               status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED', 'PICKED_UP_EARLY')),

    -- Morning arrival details
                                               arrival_time TIME,
                                               brought_by VARCHAR(100),

    -- Afternoon departure details
                                               departure_time TIME,
                                               picked_up_by VARCHAR(100),
                                               pick_up_person_name VARCHAR(200),
                                               pick_up_person_phone VARCHAR(20),

    -- Metadata
                                               notes TEXT,
                                               marked_by UUID REFERENCES auth.users(id),
                                               created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
                                               updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

                                               UNIQUE(student_id, date, session_id)
);

-- Attendance summary (materialized per term)
-- session_type comes from JOIN with attendance_sessions
CREATE MATERIALIZED VIEW attendance.attendance_summary AS
SELECT
    sa.student_id,
    sa.class_id,
    sa.term_id,
    sa.school_id,
    COUNT(*) FILTER (WHERE s.session_type = 'MORNING_ARRIVAL') AS total_morning_sessions,
    COUNT(*) FILTER (WHERE s.session_type = 'MORNING_ARRIVAL' AND sa.status IN ('PRESENT', 'LATE')) AS mornings_present,
    COUNT(*) FILTER (WHERE s.session_type = 'MORNING_ARRIVAL' AND sa.status = 'LATE') AS mornings_late,
    COUNT(*) FILTER (WHERE s.session_type = 'MORNING_ARRIVAL' AND sa.status = 'ABSENT') AS mornings_absent,
    COUNT(*) FILTER (WHERE s.session_type = 'AFTERNOON_DEPARTURE') AS total_afternoon_sessions,
    COUNT(*) FILTER (WHERE s.session_type = 'AFTERNOON_DEPARTURE' AND sa.status = 'PRESENT') AS afternoons_present,
    COUNT(*) FILTER (WHERE s.session_type = 'AFTERNOON_DEPARTURE' AND sa.status = 'PICKED_UP_EARLY') AS early_pickups,
    ROUND(
            (COUNT(*) FILTER (WHERE sa.status IN ('PRESENT', 'LATE'))::DECIMAL /
         NULLIF(COUNT(*), 0) * 100), 2
    ) AS attendance_percentage,
    NOW() AS computed_at
FROM attendance.student_attendance sa
         JOIN attendance.attendance_sessions s ON sa.session_id = s.id
GROUP BY sa.student_id, sa.class_id, sa.term_id, sa.school_id;

-- Indexes
CREATE INDEX idx_attendance_session_class_date ON attendance.attendance_sessions(class_id, date);
CREATE INDEX idx_attendance_session_term ON attendance.attendance_sessions(term_id);
CREATE INDEX idx_student_attendance_student ON attendance.student_attendance(student_id);
CREATE INDEX idx_student_attendance_date ON attendance.student_attendance(date);
CREATE INDEX idx_student_attendance_class_date ON attendance.student_attendance(class_id, date);
CREATE UNIQUE INDEX idx_student_attendance_unique ON attendance.student_attendance(student_id, date, session_id);