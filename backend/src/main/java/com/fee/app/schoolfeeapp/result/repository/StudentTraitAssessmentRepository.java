package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.StudentTraitAssessment;
import com.fee.app.schoolfeeapp.result.domain.StudentTraitAssessmentValueView;
import com.fee.app.schoolfeeapp.result.domain.StudentTraitAssessmentView;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StudentTraitAssessmentRepository extends ReactiveCrudRepository<StudentTraitAssessment, UUID> {

    @Query("""
            SELECT
                trait.name,
                trait.category,
                assessment.rating,
                assessment.comment
            FROM result.student_trait_assessments assessment
            JOIN result.assessment_traits trait ON trait.id = assessment.trait_id
            WHERE assessment.student_id = :studentId
              AND assessment.term_id = :termId
              AND assessment.school_id = :schoolId
              AND trait.school_id = :schoolId
              AND trait.is_active = true
            ORDER BY trait.category, trait.sort_order, trait.name
            """)
    Flux<StudentTraitAssessmentView> findForStudentResult(UUID studentId, UUID termId, UUID schoolId);

    @Query("""
            SELECT student_id, trait_id, rating, comment
            FROM result.student_trait_assessments
            WHERE class_id = :classId
              AND term_id = :termId
              AND school_id = :schoolId
            ORDER BY student_id, trait_id
            """)
    Flux<StudentTraitAssessmentValueView> findValuesForClass(UUID classId, UUID termId, UUID schoolId);

    @Query("""
            INSERT INTO result.student_trait_assessments (
                id, school_id, student_id, class_id, term_id, trait_id,
                rating, comment, recorded_by, created_at, updated_at
            )
            VALUES (
                :#{#assessment.id},
                :#{#assessment.schoolId},
                :#{#assessment.studentId},
                :#{#assessment.classId},
                :#{#assessment.termId},
                :#{#assessment.traitId},
                :#{#assessment.rating},
                :#{#assessment.comment},
                :#{#assessment.recordedBy},
                :#{#assessment.createdAt},
                :#{#assessment.updatedAt}
            )
            ON CONFLICT (student_id, term_id, trait_id)
            DO UPDATE SET
                class_id = EXCLUDED.class_id,
                rating = EXCLUDED.rating,
                comment = EXCLUDED.comment,
                recorded_by = EXCLUDED.recorded_by,
                updated_at = EXCLUDED.updated_at
            RETURNING *
            """)
    Mono<StudentTraitAssessment> upsert(@Param("assessment") StudentTraitAssessment assessment);
}
