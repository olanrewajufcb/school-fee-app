package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.CaScore;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface CaScoreRepository extends ReactiveCrudRepository<CaScore, UUID> {
    Flux<CaScore> findByStudentIdAndSubjectIdAndTermId(UUID studentId, UUID subjectId, UUID termId);

    @Query("""
            INSERT INTO result.ca_scores (
                id,
                school_id,
                student_id,
                subject_id,
                class_id,
                term_id,
                ca_component_id,
                score,
                max_score,
                recorded_by,
                created_at,
                updated_at
            )
            VALUES (
                :#{#score.id},
                :#{#score.schoolId},
                :#{#score.studentId},
                :#{#score.subjectId},
                :#{#score.classId},
                :#{#score.termId},
                :#{#score.caComponentId},
                :#{#score.score},
                :#{#score.maxScore},
                :#{#score.recordedBy},
                :#{#score.createdAt},
                :#{#score.updatedAt}
            )
            RETURNING *
            """)
    Mono<CaScore> insert(CaScore score);

    Flux<CaScore> findByStudentIdAndSubjectIdAndTermIdAndSchoolId(
            UUID studentId, UUID subjectId, UUID termId, UUID schoolId);

    Mono<Boolean> existsByStudentIdAndSubjectIdAndTermIdAndCaComponentId(UUID studentId, UUID subjectId, UUID termId, UUID caComponentId);

    @Query("""
            SELECT EXISTS (
                SELECT 1
                FROM result.ca_scores cs
                JOIN result.ca_components cc
                  ON cc.id = cs.ca_component_id
                 AND cc.school_id = :schoolId
                 AND cc.is_active = true
                WHERE cs.school_id = :schoolId
            )
            """)
    Mono<Boolean> existsBySchoolIdAndActiveComponents(UUID schoolId);
}
