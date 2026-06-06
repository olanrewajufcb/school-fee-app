package com.fee.app.schoolfeeapp.auth.repository;

import com.fee.app.schoolfeeapp.auth.domain.UserSchoolRole;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserSchoolRoleRepository extends ReactiveCrudRepository<UserSchoolRole, UUID> {

    /**
     * Find all active roles for a user in a school.
     */
    Flux<UserSchoolRole> findByUserIdAndSchoolId(UUID userId, UUID schoolId);

    /**
     * Find a specific role assignment.
     */
    @Query("""
SELECT *
FROM auth.user_school_roles
WHERE user_id = :userId
AND school_id = :schoolId
AND role = :role
AND is_active = true
""")
    Mono<UserSchoolRole> findByUserIdAndSchoolIdAndRoleAndIsActiveTrue(
            UUID userId,
            UUID schoolId,
            String role
    );

    Mono<UserSchoolRole> findByUserIdAndSchoolIdAndRole(UUID userId, UUID schoolId, String role);
    /**
     * Find all active roles for a user across all schools.
     */
    Flux<UserSchoolRole> findByUserIdAndIsActiveTrue(UUID userId);
}