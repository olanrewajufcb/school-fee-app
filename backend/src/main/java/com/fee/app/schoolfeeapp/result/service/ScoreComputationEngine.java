package com.fee.app.schoolfeeapp.result.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScoreComputationEngine {

    private final DatabaseClient databaseClient;

    public Mono<Void> computeFinalScores(UUID classId, UUID termId, UUID subjectId) {
        return databaseClient.sql("""
            INSERT INTO result.final_scores 
                (id, school_id, student_id, subject_id, class_id, term_id,
                 ca_total, ca_max_total, exam_score, exam_max_score, final_score, computed_at)
            SELECT 
                gen_random_uuid(), e.school_id, e.student_id, :subjectId, :classId, :termId,
                COALESCE(ca.total_ca, 0), COALESCE(ca.max_ca, 0),
                e.score, e.max_score,
                COALESCE((COALESCE(ca.total_ca, 0) / NULLIF(COALESCE(ca.max_ca, 1), 0) * 50) +
                         (e.score / NULLIF(e.max_score, 1) * 50), 0),
                NOW()
            FROM result.scores e
            JOIN school.students s ON e.student_id = s.id
            LEFT JOIN (
                SELECT student_id, SUM(score) as total_ca, SUM(max_score) as max_ca
                FROM result.ca_scores
                WHERE subject_id = :subjectId AND term_id = :termId
                GROUP BY student_id
            ) ca ON e.student_id = ca.student_id
            WHERE e.class_id = :classId AND e.term_id = :termId AND e.subject_id = :subjectId
            ON CONFLICT (student_id, subject_id, term_id) DO UPDATE SET
                ca_total = EXCLUDED.ca_total,
                ca_max_total = EXCLUDED.ca_max_total,
                exam_score = EXCLUDED.exam_score,
                exam_max_score = EXCLUDED.exam_max_score,
                final_score = EXCLUDED.final_score,
                computed_at = NOW()
            """)
            .bind("classId", classId)
            .bind("termId", termId)
            .bind("subjectId", subjectId)
            .fetch()
            .rowsUpdated()
            .doOnSuccess(rows -> log.info("Final scores computed: {} rows", rows))
            .then();
    }

    public Mono<Void> computeSubjectPositions(UUID classId, UUID termId) {
        return databaseClient.sql("""
            WITH ranked AS (
                SELECT id, RANK() OVER (
                    PARTITION BY subject_id ORDER BY final_score DESC
                ) as subject_position
                FROM result.final_scores
                WHERE class_id = :classId AND term_id = :termId
            )
            UPDATE result.final_scores fs
            SET subject_position = r.subject_position, computed_at = NOW()
            FROM ranked r WHERE fs.id = r.id
            """)
            .bind("classId", classId)
            .bind("termId", termId)
            .fetch()
            .rowsUpdated()
            .then();
    }

    public Mono<Void> computeClassRankings(UUID classId, UUID termId, UUID schoolId, RankingParameters params) {
        return databaseClient.sql("""
            WITH student_averages AS (
                SELECT student_id,
                    AVG(final_score) as avg_score,
                    SUM(final_score) as total_score,
                    COUNT(*) as subjects_taken,
                    SUM(CASE WHEN final_score >= :passMark THEN 1 ELSE 0 END) as subjects_passed
                FROM result.final_scores
                WHERE class_id = :classId AND term_id = :termId
                GROUP BY student_id
            ),
            ranked AS (
                SELECT student_id, total_score, subjects_taken, subjects_passed, avg_score,
                    RANK() OVER (ORDER BY avg_score DESC) as class_position,
                    COUNT(*) OVER () as out_of
                FROM student_averages
            )
            INSERT INTO result.class_rankings 
                (id, school_id, student_id, class_id, term_id,
                 total_score, total_max_score, average_percentage,
                 class_position, out_of, subjects_taken, subjects_passed, computed_at)
            SELECT gen_random_uuid(), :schoolId, student_id, :classId, :termId,
                total_score, :totalMaxScore, avg_score,
                class_position, out_of, subjects_taken, subjects_passed, NOW()
            FROM ranked
            ON CONFLICT (student_id, term_id) DO UPDATE SET
                total_score = EXCLUDED.total_score,
                average_percentage = EXCLUDED.average_percentage,
                class_position = EXCLUDED.class_position,
                out_of = EXCLUDED.out_of,
                subjects_taken = EXCLUDED.subjects_taken,
                subjects_passed = EXCLUDED.subjects_passed,
                computed_at = NOW()
            """)
            .bind("classId", classId)
            .bind("termId", termId)
            .bind("schoolId", schoolId)
            .bind("totalMaxScore", params.totalMaxScore())
            .bind("passMark", params.passMark())
            .fetch()
            .rowsUpdated()
            .doOnSuccess(rows -> log.info("Class rankings computed: {} students", rows))
            .then();
    }

    public record RankingParameters(int passMark, int totalMaxScore) {}
}
