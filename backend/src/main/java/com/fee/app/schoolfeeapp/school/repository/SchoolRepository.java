package com.fee.app.schoolfeeapp.school.repository;

import com.fee.app.schoolfeeapp.school.domain.School;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SchoolRepository extends ReactiveCrudRepository<School, UUID> {

    Mono<School> findByCode(String code);
    Mono<School> findByIdAndIsActiveTrue(UUID id);

    /**
     * Lock an active school row for transactional operations that must not race
     * with school deactivation.
     */
    @Query("""
        SELECT *
        FROM school.schools
        WHERE id = :id AND is_active = true
        FOR UPDATE
        """)
    Mono<School> findActiveByIdForUpdate(UUID id);

    /**
     * Find schools with optional active filter, paginated.
     */
    @Query("""
        SELECT * FROM school.schools
        WHERE (:isActive IS NULL OR is_active = :isActive)
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<School> findByActiveStatus(Boolean isActive, int limit, long offset);

    /**
     * Count schools by active status.
     */
    @Query("""
        SELECT COUNT(*) FROM school.schools
        WHERE (:isActive IS NULL OR is_active = :isActive)
        """)
    Mono<Long> countByActiveStatus(Boolean isActive);
}
