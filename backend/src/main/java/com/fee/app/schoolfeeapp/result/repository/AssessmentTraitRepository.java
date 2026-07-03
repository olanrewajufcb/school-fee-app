package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.AssessmentTrait;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface AssessmentTraitRepository extends ReactiveCrudRepository<AssessmentTrait, UUID> {

    @Query("""
            SELECT *
            FROM result.assessment_traits
            WHERE school_id = :schoolId
              AND is_active = true
              AND (education_level IS NULL OR education_level = CAST(:educationLevel AS result.education_level))
            ORDER BY category, sort_order, name
            """)
    Flux<AssessmentTrait> findActiveForLevel(UUID schoolId, String educationLevel);

    @Query("""
            SELECT *
            FROM result.assessment_traits
            WHERE id = :id
              AND school_id = :schoolId
              AND is_active = true
            """)
    Mono<AssessmentTrait> findActiveByIdAndSchoolId(UUID id, UUID schoolId);

    @Query("""
            INSERT INTO result.assessment_traits (
                id, school_id, education_level, name, category, sort_order, is_active, created_at
            )
            SELECT gen_random_uuid(),
                   :schoolId,
                   CAST(:educationLevel AS result.education_level),
                   :name,
                   :category,
                   :sortOrder,
                   true,
                   :createdAt
            WHERE NOT EXISTS (
                SELECT 1
                FROM result.assessment_traits
                WHERE school_id = :schoolId
                  AND education_level = CAST(:educationLevel AS result.education_level)
                  AND category = :category
                  AND LOWER(BTRIM(name)) = LOWER(BTRIM(:name))
                  AND is_active = true
            )
            RETURNING *
            """)
    Mono<AssessmentTrait> insertDefaultIfMissing(
            UUID schoolId,
            String educationLevel,
            String name,
            String category,
            int sortOrder,
            Instant createdAt);
}
