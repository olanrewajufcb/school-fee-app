package com.fee.app.schoolfeeapp.student.repository;


import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SchoolStudentGuardianLinkRepository extends ReactiveCrudRepository<StudentGuardianLink, UUID> {

    Flux<StudentGuardianLink> findByGuardianId(UUID guardianId);
    Flux<StudentGuardianLink> findByGuardianIdAndDeletedAtIsNull(UUID guardianId);
    Flux<StudentGuardianLink> findByStudentId(UUID studentId);
    Mono<StudentGuardianLink> findByGuardianIdAndStudentId(UUID guardianId, UUID studentId);
    Flux<StudentGuardianLink> findByStudentIdAndIsPrimaryContactTrue(UUID studentId);

    @Query("""
        SELECT *
        FROM school.student_guardian_links
        WHERE student_id = :studentId
          AND deleted_at IS NULL
        ORDER BY is_primary_contact DESC NULLS LAST, contact_priority ASC, created_at ASC
        """)
    Flux<StudentGuardianLink> findActiveByStudentId(UUID studentId);

    @Query("""
        SELECT *
        FROM school.student_guardian_links
        WHERE student_id = :studentId
          AND is_primary_contact = true
          AND deleted_at IS NULL
        ORDER BY contact_priority ASC, created_at ASC
        """)
    Flux<StudentGuardianLink> findActivePrimaryByStudentId(UUID studentId);

    /**
     * Find guardian link by user ID and student ID (for parent access check).
     */
    @Query("""
        SELECT l.* FROM school.student_guardian_links l
        JOIN school.student_guardians g ON l.guardian_id = g.id
        WHERE g.user_id = :userId AND l.student_id = :studentId
          AND l.deleted_at IS NULL AND g.deleted_at IS NULL
        """)
    Mono<StudentGuardianLink> findByGuardianUserIdAndStudentId(UUID userId, UUID studentId);

    @Query("""
        SELECT l.*
        FROM school.student_guardian_links l
        JOIN school.student_guardians g ON l.guardian_id = g.id
        JOIN school.students s ON s.id = l.student_id
        WHERE g.user_id = :userId
          AND l.student_id = :studentId
          AND s.school_id = :schoolId
          AND l.can_view_fees = true
          AND l.deleted_at IS NULL
          AND g.deleted_at IS NULL
          AND s.deleted_at IS NULL
        """)
    Mono<StudentGuardianLink> findFeeAccessByGuardianUserIdAndStudentIdAndSchoolId(
            UUID userId, UUID studentId, UUID schoolId);
}
