package com.fee.app.schoolfeeapp.school.repository;

import com.fee.app.schoolfeeapp.school.domain.Term;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface TermRepository extends ReactiveCrudRepository<Term, UUID> {

    /**
     * Find all terms for a session.
     */
    Flux<Term> findBySessionId(UUID sessionId);

    /**
     * Find all terms for a session, ordered by term number.
     */
    Flux<Term> findBySessionIdOrderByTermNumberAsc(UUID sessionId);

    /**
     * Find current terms for a school.
     * Joins through academic_sessions to get school context.
     */
    @Query("""
        SELECT t.id, t.session_id, t.name, t.term_number, 
               t.start_date, t.end_date, t.is_current, 
               t.created_at, t.created_by, t.updated_at, 
               t.updated_by, t.version
        FROM school.terms t
        JOIN school.academic_sessions s ON t.session_id = s.id
        WHERE s.school_id = :schoolId 
          AND t.is_current = true
          AND t.deleted_at IS NULL
          AND s.deleted_at IS NULL
        """)
    Flux<Term> findCurrentTermsBySchoolId(UUID schoolId);

    @Query("""
        SELECT t.*
        FROM school.terms t
        JOIN school.academic_sessions s ON t.session_id = s.id
        WHERE t.id = :termId
          AND s.school_id = :schoolId
          AND t.deleted_at IS NULL
          AND s.deleted_at IS NULL
        """)
    Mono<Term> findByIdAndSchoolId(UUID termId, UUID schoolId);

    /**
     * Lock a term row for transactional state transitions.
     */
    @Query("""
        SELECT *
        FROM school.terms
        WHERE id = :id
        FOR UPDATE
        """)
    Mono<Term> findByIdForUpdate(UUID id);

    @Query("""
        SELECT *
        FROM school.terms
        WHERE id = :id
          AND deleted_at IS NULL
        FOR UPDATE
        """)
    Mono<Term> findByIdAndDeletedAtIsNullForUpdate(UUID id);
}
