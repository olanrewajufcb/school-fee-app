-- Migration to update existing exams and scores where max_score is 100 to use their weight_percentage instead.
-- And recompute the final scores and class rankings accordingly.

-- 1. Update result.exams max_score to match weight_percentage where max_score is 100
UPDATE result.exams 
SET max_score = weight_percentage::INT 
WHERE max_score = 100;

-- 2. Update result.scores max_score to match result.exams max_score
UPDATE result.scores s
SET max_score = e.max_score
FROM result.exams e
WHERE s.exam_id = e.id;

-- 3. Recompute all final scores with the corrected formula and exam max_scores
WITH computed AS (
    SELECT
        fs.id,
        COALESCE(ca.ca_weighted_score, 0) +
        CASE WHEN e.max_score > 0
             THEN (s.score / e.max_score::DECIMAL * e.weight_percentage)
             ELSE 0
        END AS new_final_score,
        e.max_score AS new_exam_max_score
    FROM result.final_scores fs
    JOIN result.scores s ON fs.student_id = s.student_id AND fs.subject_id = s.subject_id AND fs.term_id = s.term_id
    JOIN result.exams e ON s.exam_id = e.id
    LEFT JOIN (
        SELECT cas.student_id, cas.subject_id, cas.term_id,
               SUM((cas.score / cas.max_score::DECIMAL) * cc.weight_percentage) AS ca_weighted_score
        FROM result.ca_scores cas
        JOIN result.ca_components cc ON cas.ca_component_id = cc.id
        WHERE cc.is_active = true
        GROUP BY cas.student_id, cas.subject_id, cas.term_id
    ) ca ON s.student_id = ca.student_id AND s.subject_id = ca.subject_id AND s.term_id = ca.term_id
),
graded AS (
    SELECT
        c.id,
        c.new_final_score,
        c.new_exam_max_score,
        grade_rule.rule ->> 'grade' AS new_grade,
        grade_rule.rule ->> 'remark' AS new_remark,
        COALESCE((grade_rule.rule ->> 'points')::DECIMAL, 0) AS new_points
    FROM computed c
    JOIN result.final_scores fs ON c.id = fs.id
    LEFT JOIN result.grade_configs gc ON gc.school_id = fs.school_id AND gc.is_active = true
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
        WHERE c.new_final_score >= (rule ->> 'minScore')::DECIMAL
          AND c.new_final_score <= (rule ->> 'maxScore')::DECIMAL
        LIMIT 1
    ) grade_rule ON true
)
UPDATE result.final_scores fs
SET final_score = g.new_final_score,
    exam_max_score = g.new_exam_max_score,
    grade = g.new_grade,
    remark = g.new_remark,
    points = g.new_points,
    computed_at = NOW()
FROM graded g
WHERE fs.id = g.id;

-- 4. Recompute all class rankings with the new averages
WITH student_averages AS (
    SELECT student_id, class_id, term_id, school_id,
        AVG(final_score) as avg_score,
        SUM(final_score) as total_score,
        COUNT(*) as subjects_taken,
        SUM(CASE WHEN final_score >= 40 THEN 1 ELSE 0 END) as subjects_passed
    FROM result.final_scores
    GROUP BY student_id, class_id, term_id, school_id
),
ranked AS (
    SELECT student_id, class_id, term_id, school_id, total_score, subjects_taken, subjects_passed, avg_score,
        RANK() OVER (PARTITION BY class_id, term_id ORDER BY avg_score DESC) as class_position,
        COUNT(*) OVER (PARTITION BY class_id, term_id) as out_of
    FROM student_averages
),
graded_ranking AS (
    SELECT ranked.*, grade_rule.rule ->> 'grade' AS overall_grade
    FROM ranked
    LEFT JOIN result.grade_configs gc
      ON gc.school_id = ranked.school_id AND gc.is_active = true
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
UPDATE result.class_rankings cr
SET total_score = g.total_score,
    average_percentage = g.avg_score,
    overall_grade = g.overall_grade,
    class_position = g.class_position,
    out_of = g.out_of,
    subjects_taken = g.subjects_taken,
    subjects_passed = g.subjects_passed,
    computed_at = NOW()
FROM graded_ranking g
WHERE cr.student_id = g.student_id AND cr.term_id = g.term_id;
