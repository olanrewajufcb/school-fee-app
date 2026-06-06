package com.fee.app.schoolfeeapp.auth.repository;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLinkProjection;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StudentGuardianLinkRepository extends ReactiveCrudRepository<StudentGuardianLink, UUID> {

  @Query(
"""
SELECT
  s.id,
  s.first_name,
  s.last_name,
  s.admission_number,
  c.name as class_name,
  l.relationship,
  l.can_view_fees,
  l.can_view_results,
  l.can_view_attendance
FROM school.student_guardian_links l
JOIN school.students s ON s.id = l.student_id
LEFT JOIN school.classes c ON c.id = s.current_class_id
WHERE l.guardian_id = :guardianId AND l.deletedAt = Null
""")
  Flux<StudentGuardianLinkProjection> findByGuardianIdAndDeletedAtIsNull(UUID guardianId);

  @Query(
"""
SELECT
  l.id,
  l.relationship,
  l.can_view_fees,
  l.can_view_results,
  l.can_view_attendance
FROM school.student_guardian_links l
WHERE l.deleted_at = null
""")
  Flux<StudentGuardianLink> findByGuardianId(UUID guardianId);

  @Query("""
          SELECT *
          FROM school.student_guardian_links
          WHERE student_id = :studentId
            AND is_primary_contact = true
            AND deleted_at IS NULL
          ORDER BY contact_priority ASC NULLS LAST, created_at ASC
          """)
  Flux<StudentGuardianLink> findByStudentIdAndIsPrimaryContactTrue(UUID studentId);

  @Query("""
          SELECT *
          FROM school.student_guardian_links
          WHERE guardian_id = :guardianId
            AND student_id = :studentId
            AND deleted_at IS NULL
          """)
  Mono<StudentGuardianLink> findByGuardianIdAndStudentIdAndDeletedAtIsNull(
          UUID guardianId, UUID studentId);

}
