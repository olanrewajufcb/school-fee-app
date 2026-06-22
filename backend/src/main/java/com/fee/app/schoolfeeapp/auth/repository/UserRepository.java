package com.fee.app.schoolfeeapp.auth.repository;

import com.fee.app.schoolfeeapp.auth.domain.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface UserRepository extends ReactiveCrudRepository<User, UUID> {

    Mono<User> findByKeycloakIdAndDeletedAtIsNull(UUID keycloakId);
    Mono<User> findByIdAndSchoolIdAndDeletedAtIsNull(UUID id, UUID schoolId);

    /**
     * Paginated query with search support.
     */
    @Query("""
        SELECT * FROM auth.users 
        WHERE school_id = :schoolId 
          AND deleted_at IS NULL
          AND (:userType IS NULL OR user_type = :userType)
          AND (:isActive IS NULL OR is_active = :isActive)
          AND (:search IS NULL OR 
               LOWER(first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(last_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(email) LIKE LOWER(CONCAT('%', :search, '%')) OR
               phone LIKE CONCAT('%', :search, '%'))
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<User> findBySchoolIdWithFilters(
            UUID schoolId,
            String userType,
            Boolean isActive,
            String search,
            int limit,
            long offset
    );

    /**
     * Count with filters for total pages calculation.
     */
    @Query("""
        SELECT COUNT(*) FROM auth.users 
        WHERE school_id = :schoolId 
          AND deleted_at IS NULL
          AND (:userType IS NULL OR user_type = :userType)
          AND (:isActive IS NULL OR is_active = :isActive)
          AND (:search IS NULL OR 
               LOWER(first_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(last_name) LIKE LOWER(CONCAT('%', :search, '%')) OR
               LOWER(email) LIKE LOWER(CONCAT('%', :search, '%')) OR
               phone LIKE CONCAT('%', :search, '%'))
        """)
    Mono<Long> countBySchoolIdWithFilters(
            UUID schoolId,
            String userType,
            Boolean isActive,
            String search
    );


    /**
     * Update user's Keycloak ID (used by outbox processor for staff creation).
     */
    @Query("""
        UPDATE auth.users 
        SET keycloak_id = :keycloakId,
            updated_at = NOW()
        WHERE id = :userId
        RETURNING *
        """)
    Mono<User> updateKeycloakId(UUID userId, UUID keycloakId);
}
