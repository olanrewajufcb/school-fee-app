package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.Subject;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.data.r2dbc.repository.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface SubjectRepository extends ReactiveCrudRepository<Subject, UUID> {
    @Query("""
            SELECT *
            FROM result.subjects
            WHERE school_id = :schoolId AND is_active = true
            ORDER BY LOWER(name), id
            """)
    Flux<Subject> findActiveBySchoolIdOrderByName(UUID schoolId);

    Mono<Subject> findByIdAndSchoolIdAndIsActiveTrue(UUID id, UUID schoolId);

    @Query("""
            SELECT *
            FROM result.subjects
            WHERE id = :id AND school_id = :schoolId AND is_active = true
            FOR UPDATE
            """)
    Mono<Subject> findActiveByIdAndSchoolIdForUpdate(UUID id, UUID schoolId);

    @Query("""
            SELECT EXISTS(
                SELECT 1 FROM result.subjects
                WHERE school_id = :schoolId
                  AND LOWER(BTRIM(name)) = LOWER(BTRIM(:name))
                  AND (CAST(:excludedId AS UUID) IS NULL OR id <> :excludedId)
            )
            """)
    Mono<Boolean> existsByNormalizedName(UUID schoolId, String name, UUID excludedId);

    @Query("""
            SELECT EXISTS(
                SELECT 1 FROM result.subjects
                WHERE school_id = :schoolId
                  AND code IS NOT NULL
                  AND LOWER(BTRIM(code)) = LOWER(BTRIM(:code))
                  AND (CAST(:excludedId AS UUID) IS NULL OR id <> :excludedId)
            )
            """)
    Mono<Boolean> existsByNormalizedCode(UUID schoolId, String code, UUID excludedId);
}
