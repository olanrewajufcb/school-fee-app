package com.fee.app.schoolfeeapp.student.repository;

import com.fee.app.schoolfeeapp.student.domain.AttendanceSession;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.UUID;

public interface AttendanceSessionRepository extends ReactiveCrudRepository<AttendanceSession, UUID> {

    Mono<AttendanceSession> findByClassIdAndDateAndSessionType(UUID classId, LocalDate date, String sessionType);

    @Query("""
        SELECT *
        FROM attendance.attendance_sessions
        WHERE id = :sessionId
        FOR UPDATE
        """)
    Mono<AttendanceSession> findByIdForUpdate(UUID sessionId);

    Flux<AttendanceSession> findByClassIdAndDate(UUID classId, LocalDate date);

    Flux<AttendanceSession> findByClassIdAndTermId(UUID classId, UUID termId);
}
