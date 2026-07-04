package com.fee.app.schoolfeeapp.result.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fee.app.schoolfeeapp.result.domain.ReportCardTemplate;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface ReportCardTemplateRepository extends ReactiveCrudRepository<ReportCardTemplate, UUID> {

    @Query("""
            INSERT INTO result.report_card_templates (
                school_id, name, education_level, config, is_default, is_active, created_at, updated_at, version
            )
            VALUES (
                :schoolId,
                :name,
                CAST(:educationLevel AS result.education_level),
                :config,
                :isDefault,
                true,
                :createdAt,
                :updatedAt,
                0
            )
            RETURNING *
            """)
    Mono<ReportCardTemplate> insert(
            UUID schoolId,
            String name,
            String educationLevel,
            JsonNode config,
            boolean isDefault,
            Instant createdAt,
            Instant updatedAt);

    @Query("""
            SELECT *
            FROM result.report_card_templates
            WHERE school_id = :schoolId
              AND is_active = true
            ORDER BY education_level, is_default DESC, LOWER(name), id
            """)
    Flux<ReportCardTemplate> findActiveBySchoolId(UUID schoolId);

    @Query("""
            SELECT *
            FROM result.report_card_templates
            WHERE id = :id
              AND school_id = :schoolId
              AND is_active = true
            """)
    Mono<ReportCardTemplate> findActiveByIdAndSchoolId(UUID id, UUID schoolId);

    @Query("""
            SELECT EXISTS(
                SELECT 1
                FROM result.report_card_templates
                WHERE school_id = :schoolId
                  AND education_level = CAST(:educationLevel AS result.education_level)
                  AND LOWER(BTRIM(name)) = LOWER(BTRIM(:name))
                  AND is_active = true
            )
            """)
    Mono<Boolean> existsActiveByNormalizedName(UUID schoolId, String educationLevel, String name);

    @Query("""
            UPDATE result.report_card_templates
            SET is_default = false,
                updated_at = :updatedAt,
                version = version + 1
            WHERE school_id = :schoolId
              AND education_level = CAST(:educationLevel AS result.education_level)
              AND is_active = true
              AND is_default = true
            """)
    Mono<Integer> clearDefaultForLevel(UUID schoolId, String educationLevel, Instant updatedAt);
}
