package com.fee.app.schoolfeeapp.school.repository;

import com.fee.app.schoolfeeapp.school.domain.AcademicSession;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface AcademicSessionRepository extends ReactiveCrudRepository<AcademicSession, UUID> {

    /**
     * Lock a session row for transactional state transitions.
     */
    @Query("""
        SELECT *
        FROM school.academic_sessions
        WHERE id = :id
        FOR UPDATE
        """)
    Mono<AcademicSession> findByIdForUpdate(UUID id);

    Mono<AcademicSession> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * Find all sessions for a school, ordered by start date descending.
     */
    Flux<AcademicSession> findBySchoolIdOrderByStartDateDesc(UUID schoolId);

    /**
     * Find the current session for a school.
     */
    Mono<AcademicSession> findBySchoolIdAndIsCurrentTrue(UUID schoolId);

    /**
     * Find all sessions for a school with a specific status.
     */
    Flux<AcademicSession> findBySchoolIdAndIsCurrent(UUID schoolId, boolean isCurrent);
}
