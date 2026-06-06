package com.fee.app.schoolfeeapp.fee.repository;

import com.fee.app.schoolfeeapp.fee.domain.FeeStructure;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;


public interface FeeStructureRepository extends ReactiveCrudRepository<FeeStructure, UUID> {

    Mono<FeeStructure> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Query("""
        SELECT *
        FROM fee.fee_structures
        WHERE id = :id
          AND school_id = :schoolId
          AND deleted_at IS NULL
        FOR UPDATE
        """)
    Mono<FeeStructure> findByIdAndSchoolIdForUpdate(UUID id, UUID schoolId);

    Flux<FeeStructure> findBySchoolIdAndStatus(UUID schoolId, String status);

    Flux<FeeStructure> findBySchoolIdAndTermIdAndStatus(UUID schoolId, UUID termId, String status);

    Flux<FeeStructure> findBySchoolIdAndAcademicSessionId(UUID schoolId, UUID sessionId);

    @Query("""
        SELECT EXISTS (
            SELECT 1
            FROM fee.fee_structures
            WHERE school_id = :schoolId
              AND term_id = :termId
              AND status = 'ACTIVE'
              AND deleted_at IS NULL
              AND LOWER(name) = LOWER(:name)
        )
        """)
    Mono<Boolean> existsActiveBySchoolIdAndTermIdAndNameIgnoreCase(UUID schoolId, UUID termId, String name);
}
