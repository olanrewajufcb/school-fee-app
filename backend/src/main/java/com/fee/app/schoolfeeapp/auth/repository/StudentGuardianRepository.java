package com.fee.app.schoolfeeapp.auth.repository;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StudentGuardianRepository extends ReactiveCrudRepository<StudentGuardian, UUID> {


    Mono<StudentGuardian> findByUserIdAndDeletedAtIsNull(UUID userId);

    Mono<StudentGuardian> findByIdAndDeletedAtIsNull(UUID id);

    Mono<StudentGuardian> findByPhoneAndSchoolIdAndDeletedAtIsNull(String phone, UUID schoolId);

    Mono<StudentGuardian> findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(String phone, UUID schoolId);

    /**
     * Link guardian to user account (used during parent creation).
     */
    @Query("""
        UPDATE school.student_guardians 
        SET user_id = :userId,
            updated_at = NOW()
        WHERE id = :guardianId
        RETURNING *
        """)
    Mono<StudentGuardian> updateUserId(UUID guardianId, UUID userId);

}
