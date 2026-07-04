package com.fee.app.schoolfeeapp.student.repository;

import com.fee.app.schoolfeeapp.student.domain.StudentAttendance;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public interface StudentAttendanceRepository extends ReactiveCrudRepository<StudentAttendance, UUID> {

    Flux<StudentAttendance> findBySessionId(UUID sessionId);

    Flux<StudentAttendance> findByStudentIdAndTermIdOrderByDateDesc(UUID studentId, UUID termId);

    Flux<StudentAttendance> findByStudentIdAndDate(UUID studentId, LocalDate date);

    Mono<StudentAttendance> findByStudentIdAndSessionId(UUID studentId, UUID sessionId);

    @Query("""
        INSERT INTO attendance.student_attendance (
            school_id, session_id, student_id, class_id, term_id, date, status,
            arrival_time, brought_by, departure_time, picked_up_by,
            pick_up_person_name, pick_up_person_phone, notes, marked_by
        )
        VALUES (
            :schoolId, :sessionId, :studentId, :classId, :termId, :date, :status,
            :arrivalTime, :broughtBy, :departureTime, :pickedUpBy,
            :pickUpPersonName, :pickUpPersonPhone, :notes, :markedBy
        )
        ON CONFLICT (student_id, date, session_id)
        DO UPDATE SET
            class_id = EXCLUDED.class_id,
            term_id = EXCLUDED.term_id,
            status = EXCLUDED.status,
            arrival_time = EXCLUDED.arrival_time,
            brought_by = EXCLUDED.brought_by,
            departure_time = EXCLUDED.departure_time,
            picked_up_by = EXCLUDED.picked_up_by,
            pick_up_person_name = EXCLUDED.pick_up_person_name,
            pick_up_person_phone = EXCLUDED.pick_up_person_phone,
            notes = EXCLUDED.notes,
            marked_by = EXCLUDED.marked_by,
            updated_at = NOW()
        RETURNING *
        """)
    Mono<StudentAttendance> upsertAttendanceMark(
            UUID schoolId,
            UUID sessionId,
            UUID studentId,
            UUID classId,
            UUID termId,
            LocalDate date,
            String status,
            LocalTime arrivalTime,
            String broughtBy,
            LocalTime departureTime,
            String pickedUpBy,
            String pickUpPersonName,
            String pickUpPersonPhone,
            String notes,
            UUID markedBy);

    @Query("""
        SELECT st.*
        FROM attendance.student_attendance st
        JOIN attendance.attendance_sessions ats ON ats.id = st.session_id
        WHERE st.class_id = :classId
          AND st.date = :date
        ORDER BY ats.session_type ASC, st.created_at ASC
        """)
    Flux<StudentAttendance> findByClassIdAndDate(UUID classId, LocalDate date);

    @Query("""
    SELECT
        st.id, st.school_id, st.session_id, st.student_id, st.class_id, st.term_id,
        st.date, st.status, st.arrival_time, st.brought_by, st.departure_time,
        st.picked_up_by, st.pick_up_person_name, st.pick_up_person_phone,
        st.notes, st.marked_by, st.created_at, st.updated_at,
        ats.session_type
    FROM attendance.student_attendance st
    JOIN attendance.attendance_sessions ats ON ats.id = st.session_id
    WHERE st.student_id = :studentId AND st.date = :date AND ats.session_type = :sessionType
    """)
    Mono<StudentAttendance> findByStudentIdAndDateAndSessionType(UUID studentId, LocalDate date, String sessionType);


    @Query("""
        SELECT COUNT(*) FILTER (WHERE status IN ('PRESENT', 'LATE')) AS present,
               COUNT(*) FILTER (WHERE status = 'ABSENT') AS absent,
               COUNT(*) FILTER (WHERE status = 'LATE') AS late,
               COUNT(*) AS total
        FROM attendance.student_attendance st
        JOIN attendance.attendance_sessions ats ON ats.id = st.session_id
        WHERE st.student_id = :studentId
          AND st.term_id = :termId
          AND ats.session_type = 'MORNING_ARRIVAL'
        """)
    Mono<AttendanceCounts> getAttendanceCounts(UUID studentId, UUID termId);

    interface AttendanceCounts {
        Long getPresent();
        Long getAbsent();
        Long getLate();
        Long getTotal();
    }
}
