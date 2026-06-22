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
            WITH computed_scores AS (
                SELECT
                    gen_random_uuid() AS id,
                    e.school_id,
                    e.student_id,
                    :subjectId AS subject_id,
                    :classId AS class_id,
                    :termId AS term_id,
                    COALESCE(ca.total_ca, 0) AS ca_total,
                    COALESCE(ca.max_ca, 0) AS ca_max_total,
                    e.score AS exam_score,
                    e.max_score AS exam_max_score,
                    COALESCE(
                        COALESCE(ca.ca_weighted_score, 0) +
                        CASE WHEN e.max_score > 0
                             THEN (e.score / e.max_score::DECIMAL
                                   * COALESCE(ex.weight_percentage, 60))
                             ELSE 0
                        END,
                        0
                    ) AS final_score
                FROM result.scores e
                JOIN school.students s ON e.student_id = s.id
                JOIN result.exams ex ON e.exam_id = ex.id
                LEFT JOIN (
                    SELECT cas.student_id, 
                           SUM(cas.score) AS total_ca, 
                           SUM(cas.max_score) AS max_ca,
                           SUM((cas.score / cas.max_score::DECIMAL) * cc.weight_percentage) AS ca_weighted_score
                    FROM result.ca_scores cas
                    JOIN result.ca_components cc ON cas.ca_component_id = cc.id
                    WHERE cas.subject_id = :subjectId 
                      AND cas.term_id = :termId
                      AND cc.is_active = true
                    GROUP BY cas.student_id
                ) ca ON e.student_id = ca.student_id
                WHERE e.class_id = :classId
                  AND e.term_id = :termId
                  AND e.subject_id = :subjectId
            )
            INSERT INTO result.final_scores
                (id, school_id, student_id, subject_id, class_id, term_id,
                 ca_total, ca_max_total, exam_score, exam_max_score, final_score,
                 grade, remark, points, computed_at)
            SELECT
                cs.id, cs.school_id, cs.student_id, cs.subject_id, cs.class_id, cs.term_id,
                cs.ca_total, cs.ca_max_total, cs.exam_score, cs.exam_max_score, cs.final_score,
                grade_rule.rule ->> 'grade',
                grade_rule.rule ->> 'remark',
                COALESCE((grade_rule.rule ->> 'points')::DECIMAL, 0),
                NOW()
            FROM computed_scores cs
            LEFT JOIN result.grade_configs gc
              ON gc.school_id = cs.school_id AND gc.is_active = true
            LEFT JOIN LATERAL (
                SELECT rule
                FROM jsonb_array_elements(COALESCE(
                    gc.config -> 'grades',
                    '[
                      {"grade":"A1","minScore":75,"maxScore":100,"remark":"Excellent","points":4.0},
                      {"grade":"B2","minScore":70,"maxScore":74,"remark":"Very Good","points":3.5},
                      {"grade":"B3","minScore":65,"maxScore":69,"remark":"Good","points":3.0},
                      {"grade":"C4","minScore":60,"maxScore":64,"remark":"Credit","points":2.5},
                      {"grade":"C5","minScore":55,"maxScore":59,"remark":"Credit","points":2.0},
                      {"grade":"C6","minScore":50,"maxScore":54,"remark":"Credit","points":1.5},
                      {"grade":"D7","minScore":45,"maxScore":49,"remark":"Pass","points":1.0},
                      {"grade":"E8","minScore":40,"maxScore":44,"remark":"Pass","points":0.5},
                      {"grade":"F9","minScore":0,"maxScore":39,"remark":"Fail","points":0.0}
                    ]'::jsonb
                )) AS rule
                WHERE cs.final_score >= (rule ->> 'minScore')::DECIMAL
                  AND cs.final_score <= (rule ->> 'maxScore')::DECIMAL
                LIMIT 1
            ) grade_rule ON true
            ON CONFLICT (student_id, subject_id, term_id) DO UPDATE SET
                ca_total = EXCLUDED.ca_total,
                ca_max_total = EXCLUDED.ca_max_total,
                exam_score = EXCLUDED.exam_score,
                exam_max_score = EXCLUDED.exam_max_score,
                final_score = EXCLUDED.final_score,
                grade = EXCLUDED.grade,
                remark = EXCLUDED.remark,
                points = EXCLUDED.points,
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
            ),
            graded AS (
                SELECT ranked.*, grade_rule.rule ->> 'grade' AS overall_grade
                FROM ranked
                LEFT JOIN result.grade_configs gc
                  ON gc.school_id = :schoolId AND gc.is_active = true
                LEFT JOIN LATERAL (
                    SELECT rule
                    FROM jsonb_array_elements(COALESCE(
                        gc.config -> 'grades',
                        '[
                          {"grade":"A1","minScore":75,"maxScore":100},
                          {"grade":"B2","minScore":70,"maxScore":74},
                          {"grade":"B3","minScore":65,"maxScore":69},
                          {"grade":"C4","minScore":60,"maxScore":64},
                          {"grade":"C5","minScore":55,"maxScore":59},
                          {"grade":"C6","minScore":50,"maxScore":54},
                          {"grade":"D7","minScore":45,"maxScore":49},
                          {"grade":"E8","minScore":40,"maxScore":44},
                          {"grade":"F9","minScore":0,"maxScore":39}
                        ]'::jsonb
                    )) AS rule
                    WHERE ranked.avg_score >= (rule ->> 'minScore')::DECIMAL
                      AND ranked.avg_score <= (rule ->> 'maxScore')::DECIMAL
                    LIMIT 1
                ) grade_rule ON true
            )
            INSERT INTO result.class_rankings
                (id, school_id, student_id, class_id, term_id,
                 total_score, total_max_score, average_percentage,
                 overall_grade, class_position, out_of,
                 subjects_taken, subjects_passed, computed_at)
            SELECT gen_random_uuid(), :schoolId, student_id, :classId, :termId,
                total_score, :totalMaxScore, avg_score,
                overall_grade, class_position, out_of,
                subjects_taken, subjects_passed, NOW()
            FROM graded
            ON CONFLICT (student_id, term_id) DO UPDATE SET
                total_score = EXCLUDED.total_score,
                average_percentage = EXCLUDED.average_percentage,
                overall_grade = EXCLUDED.overall_grade,
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
