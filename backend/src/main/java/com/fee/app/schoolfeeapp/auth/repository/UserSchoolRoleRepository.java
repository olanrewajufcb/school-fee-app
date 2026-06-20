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

    Mono<UserSchoolRole> findByUserIdAndSchoolIdAndRoleAndIsActiveTrue(UUID userId, UUID schoolId, String role);
    Mono<UserSchoolRole> findByUserIdAndSchoolIdIsNullAndRoleAndIsActiveTrue(UUID userId, String role);

    Mono<UserSchoolRole> findByUserIdAndSchoolIdAndRole(UUID userId, UUID schoolId, String role);
    Mono<UserSchoolRole> findByUserIdAndSchoolIdIsNullAndRole(UUID userId, String role);

    Flux<UserSchoolRole> findByUserIdAndSchoolIdIsNull(UUID userId);
    /**
     * Find all active roles for a user across all schools.
     */
    Flux<UserSchoolRole> findByUserIdAndIsActiveTrue(UUID userId);
}