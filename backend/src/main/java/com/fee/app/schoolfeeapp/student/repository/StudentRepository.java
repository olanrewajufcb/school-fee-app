package com.fee.app.schoolfeeapp.student.repository;

import com.fee.app.schoolfeeapp.student.domain.Student;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface StudentRepository extends ReactiveCrudRepository<Student, UUID> {


    Flux<Student> findByCurrentClassId(UUID classId);
    Mono<Long> countByCurrentClassId(UUID classId);
    Mono<Student> findByIdAndSchoolIdAndDeletedAtIsNull(UUID id, UUID schoolId);

    Mono<Student> findByIdAndDeletedAtIsNull(UUID id);
    Mono<Long> countBySchoolIdAndDeletedAtIsNull(UUID schoolId);

    Flux<Student> findByCurrentClassIdIn(List<UUID> classIds);
    Mono<Long> countByCurrentClassIdIn(List<UUID> classIds);

    @Query("""
        SELECT *
        FROM school.students
        WHERE school_id = :schoolId
          AND current_class_id IN (:classIds)
          AND enrollment_status = 'ACTIVE'
          AND deleted_at IS NULL
        ORDER BY last_name ASC, first_name ASC, admission_number ASC
        """)
    Flux<Student> findActiveBySchoolIdAndCurrentClassIdIn(UUID schoolId, List<UUID> classIds);

    @Query("""
        SELECT COUNT(*)
        FROM school.students
        WHERE school_id = :schoolId
          AND current_class_id IN (:classIds)
          AND enrollment_status = 'ACTIVE'
          AND deleted_at IS NULL
        """)
    Mono<Long> countActiveBySchoolIdAndCurrentClassIdIn(UUID schoolId, List<UUID> classIds);

    /**
     * Paginated student search with filters.
     */
    @Query("""
        SELECT * FROM school.students
        WHERE school_id = :schoolId
          AND deleted_at IS NULL
          AND (:classId IS NULL OR current_class_id = :classId)
          AND (:isActive IS NULL OR 
               (:isActive = true AND enrollment_status = 'ACTIVE') OR
               (:isActive = false AND enrollment_status != 'ACTIVE'))
          AND (:search IS NULL OR
               LOWER(first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(last_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(admission_number) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<Student> findBySchoolIdWithFilters(
            UUID schoolId, UUID classId, Boolean isActive, String search,
            int limit, long offset);

    @Query("""
        SELECT COUNT(*) FROM school.students
        WHERE school_id = :schoolId
          AND deleted_at IS NULL
          AND (:classId IS NULL OR current_class_id = :classId)
          AND (:isActive IS NULL OR 
               (:isActive = true AND enrollment_status = 'ACTIVE') OR
               (:isActive = false AND enrollment_status != 'ACTIVE'))
          AND (:search IS NULL OR
               LOWER(first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(last_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(admission_number) LIKE LOWER(CONCAT('%', :search, '%')))
        """)
    Mono<Long> countBySchoolIdWithFilters(
            UUID schoolId, UUID classId, Boolean isActive, String search);

    @Query("""
        SELECT *
        FROM school.students
        WHERE school_id = :schoolId
          AND current_class_id = :classId
          AND deleted_at IS NULL
        ORDER BY last_name ASC, first_name ASC, admission_number ASC
        """)
    Flux<Student> findActiveBySchoolIdAndCurrentClassId(UUID schoolId, UUID classId);

    @Query("""
        SELECT COUNT(*)
        FROM school.students
        WHERE current_class_id = :classId
          AND deleted_at IS NULL
        """)
    Mono<Long> countActiveByCurrentClassId(UUID classId);

    @Query("""
        SELECT *
        FROM school.students
        WHERE id = :id
          AND school_id = :schoolId
          AND deleted_at IS NULL
        FOR UPDATE
        """)
    Mono<Student> findByIdAndSchoolIdAndDeletedAtIsNullForUpdate(UUID id, UUID schoolId);

}
