package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.CaComponent;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface CaComponentRepository extends ReactiveCrudRepository<CaComponent, UUID> {

    @Query("""
            INSERT INTO result.ca_components (
                id,
                school_id,
                name,
                max_score,
                weight_percentage,
                sort_order,
                is_active,
                created_at
            )
            VALUES (
                :#{#component.id},
                :#{#component.schoolId},
                :#{#component.name},
                :#{#component.maxScore},
                :#{#component.weightPercentage},
                :#{#component.sortOrder},
                :#{#component.active},
                :#{#component.createdAt}
            )
            RETURNING *
            """)
    Mono<CaComponent> insert(CaComponent component);

    @Query("""
            SELECT *
            FROM result.ca_components
            WHERE school_id = :schoolId
              AND is_active = true
            ORDER BY sort_order ASC, created_at ASC, name ASC
            """)
    Flux<CaComponent> findBySchoolIdAndIsActiveTrue(UUID schoolId);

    Mono<CaComponent> findByIdAndSchoolIdAndIsActiveTrue(UUID id, UUID schoolId);

    @Modifying
    @Query("""
            UPDATE result.ca_components
            SET is_active = false
            WHERE school_id = :schoolId
              AND is_active = true
            """)
    Mono<Integer> deactivateActiveBySchoolId(UUID schoolId);
}
