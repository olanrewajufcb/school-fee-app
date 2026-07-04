package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.ClassSubject;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ClassSubjectRepository extends ReactiveCrudRepository<ClassSubject, UUID> {
    Flux<ClassSubject> findByClassIdAndIsActiveTrue(UUID classId);

    @Query("""
            SELECT *
            FROM result.class_subjects
            WHERE class_id = :classId
              AND school_id = :schoolId
              AND is_active = true
            ORDER BY created_at, id
            """)
    Flux<ClassSubject> findActiveByClassIdAndSchoolId(UUID classId, UUID schoolId);

    Mono<ClassSubject> findByClassIdAndSubjectIdAndSchoolIdAndIsActiveTrue(
            UUID classId, UUID subjectId, UUID schoolId);

    @Query("""
            SELECT *
            FROM result.class_subjects
            WHERE class_id = :classId
              AND subject_id = :subjectId
              AND school_id = :schoolId
            FOR UPDATE
            """)
    Mono<ClassSubject> findByClassAndSubjectForUpdate(
            UUID classId, UUID subjectId, UUID schoolId);

    @Query("""
            UPDATE result.class_subjects
            SET is_active = false,
                updated_at = NOW(),
                version = version + 1
            WHERE subject_id = :subjectId
              AND school_id = :schoolId
              AND is_active = true
            """)
    Mono<Integer> deactivateActiveBySubjectIdAndSchoolId(UUID subjectId, UUID schoolId);
}
