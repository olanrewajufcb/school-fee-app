package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.ResultScore;
import org.springframework.data.repository.query.Param;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ScoreRepository extends ReactiveCrudRepository<ResultScore, UUID> {

    @Query("""
            INSERT INTO result.scores (
                id,
                school_id,
                exam_id,
                student_id,
                subject_id,
                class_id,
                term_id,
                score,
                max_score,
                grade,
                remark,
                points,
                recorded_by,
                created_at,
                updated_at
            )
            VALUES (
                :#{#score.id},
                :#{#score.schoolId},
                :#{#score.examId},
                :#{#score.studentId},
                :#{#score.subjectId},
                :#{#score.classId},
                :#{#score.termId},
                :#{#score.score},
                :#{#score.maxScore},
                :#{#score.grade},
                :#{#score.remark},
                :#{#score.points},
                :#{#score.recordedBy},
                :#{#score.createdAt},
                :#{#score.updatedAt}
            )
            RETURNING *
            """)
    Mono<ResultScore> insert(@Param("score") ResultScore score);

    @Query("""
            SELECT *
            FROM result.scores
            WHERE id = :scoreId
              AND school_id = :schoolId
            FOR UPDATE
            """)
    Mono<ResultScore> findByIdAndSchoolIdForUpdate(UUID scoreId, UUID schoolId);

    @Query("""
            SELECT *
            FROM result.scores
            WHERE student_id = :studentId
              AND subject_id = :subjectId
              AND term_id = :termId
              AND school_id = :schoolId
            ORDER BY updated_at DESC NULLS LAST, created_at DESC NULLS LAST
            LIMIT 1
            """)
    Mono<ResultScore> findLatestByStudentIdAndSubjectIdAndTermIdAndSchoolId(
            UUID studentId,
            UUID subjectId,
            UUID termId,
            UUID schoolId);
}
