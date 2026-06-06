package com.fee.app.schoolfeeapp.school.repository;

import com.fee.app.schoolfeeapp.school.domain.ClassEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ClassRepository extends ReactiveCrudRepository<ClassEntity, UUID> {

    /**
     * Find class by ID and school (tenant isolation).
     */
    Mono<ClassEntity> findByIdAndSchoolId(UUID id, UUID schoolId);

    /**
     * Lock a class row for transactional state changes.
     */
    @Query("""
        SELECT *
        FROM school.classes
        WHERE id = :id AND school_id = :schoolId
        FOR UPDATE
        """)
    Mono<ClassEntity> findByIdAndSchoolIdForUpdate(UUID id, UUID schoolId);

    Flux<ClassEntity> findBySchoolIdAndIsActive(UUID schoolId, boolean isActive);
    Flux<ClassEntity> findBySchoolIdAndAcademicSessionIdAndIsActive(UUID schoolId, UUID sessionId, boolean isActive);
    Flux<ClassEntity> findBySchoolIdAndGradeLevelAndIsActive(UUID schoolId, String gradeLevel, boolean isActive);
    Flux<ClassEntity> findBySchoolIdAndAcademicSessionIdAndGradeLevelAndIsActive(
            UUID schoolId, UUID sessionId, String gradeLevel, boolean isActive);
}
