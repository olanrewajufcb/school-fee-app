package com.fee.app.schoolfeeapp.auth.service.impl;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.domain.UserSchoolRole;
import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserSchoolRoleRepository;
import com.fee.app.schoolfeeapp.auth.service.AuthService;
import com.fee.app.schoolfeeapp.auth.service.GuardianLinkingService;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.utils.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserSchoolRoleRepository roleRepository;
    private final GuardianLinkingService guardianLinkingService;
    private final JwtUtils jwtUtils;
    private final TransactionalOperator transactionalOperator;

    /**
     * Get current user profile.
     *
     * Extracts JWT user ONCE, then passes it through the entire chain.
     * All downstream methods receive jwtUser as a parameter.
     */
    public Mono<UserProfileResponse> getCurrentUserProfile() {
        return jwtUtils.getCurrentUser()
                .flatMap(jwtUser -> transactionalOperator.transactional(
                        getOrCreateUser(jwtUser)
                                .flatMap(this::touchLastLogin)
                                .flatMap(dbUser -> syncRolesFromJwt(dbUser, jwtUser))
                                .flatMap(dbUser -> enrichWithGuardianData(dbUser, jwtUser))
                ));
    }

    /**
     * Find existing user or create a new one with race condition protection.
     */
    private Mono<User> getOrCreateUser(SchoolFeeUser jwtUser) {
        UUID keycloakId = jwtUser.getUserId();
        return userRepository.findByKeycloakIdAndDeletedAtIsNull(keycloakId)
                .switchIfEmpty(Mono.defer(() -> createUserWithRaceProtection(jwtUser)));
    }

    /**
     * Update last login timestamp.
     */
    private Mono<User> touchLastLogin(User dbUser) {
        if(dbUser.getLastLogin() == null
                || dbUser.getLastLogin().isBefore(ZonedDateTime.now().minus(Duration.ofMinutes(15)))){
            dbUser.setLastLogin(ZonedDateTime.now());
            return userRepository.save(dbUser);
        }
        return Mono.just(dbUser);
    }

    /**
     * Sync roles from JWT to database.
     *
     * JWT is authoritative for current session roles.
     * DB roles are a cache for admin views and offline queries.
     */
    private Mono<User> syncRolesFromJwt(User dbUser, SchoolFeeUser jwtUser) {
        Set<String> jwtRoles = jwtUser.getRoles();

        if (jwtRoles == null || jwtRoles.isEmpty()) {
            return Mono.just(dbUser);
        }

        return roleRepository.findByUserIdAndSchoolId(dbUser.getId(), jwtUser.getSchoolId())
                .map(UserSchoolRole::getRole)
                .collect(Collectors.toSet())
                .flatMap(existingRoles -> {
                    Set<String> toAdd = jwtRoles.stream()
                            .filter(role -> !existingRoles.contains(role))
                            .collect(Collectors.toSet());

                    Set<String> toRemove = existingRoles.stream()
                            .filter(role -> !jwtRoles.contains(role))
                            .collect(Collectors.toSet());

                    return addRoles(dbUser, jwtUser, toAdd)
                            .then(deactivateRoles(dbUser, jwtUser, toRemove))
                            .thenReturn(dbUser);
                });
    }
    /**
     * Add new roles from JWT to database.
     * Reactivates previously deactivated roles instead of creating duplicates.
     */
    private Mono<Void> addRoles(User dbUser, SchoolFeeUser jwtUser, Set<String> rolesToAdd) {
        if (rolesToAdd.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(rolesToAdd)
                .flatMap(role -> roleRepository
                        .findByUserIdAndSchoolIdAndRole(dbUser.getId(), jwtUser.getSchoolId(), role)
                        .flatMap(existing -> {
                            if (Boolean.FALSE.equals(existing.getIsActive())) {
                                existing.setIsActive(true);
                                return roleRepository.save(existing);
                            }
                            return Mono.just(existing);
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            UserSchoolRole newRole = UserSchoolRole.builder()
                                    .id(UUID.randomUUID())
                                    .userId(dbUser.getId())
                                    .schoolId(jwtUser.getSchoolId())
                                    .role(role)
                                    .assignedBy(dbUser.getId())
                                    .isActive(true)
                                    .build();
                            return roleRepository.save(newRole);
                        }))
                )
                .then()
                .doOnSuccess(v -> log.debug("Added {} roles for user {}", rolesToAdd.size(), dbUser.getId()));
    }

    /**
     * Deactivate roles present in DB but absent from JWT.
     */
    private Mono<Void> deactivateRoles(User dbUser, SchoolFeeUser jwtUser, Set<String> rolesToRemove) {
        if (rolesToRemove.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(rolesToRemove)
                .flatMap(role -> roleRepository
                        .findByUserIdAndSchoolIdAndRoleAndIsActiveTrue(dbUser.getId(), jwtUser.getSchoolId(), role)
                        .flatMap(existing -> {
                            existing.setIsActive(false);
                            return roleRepository.save(existing);
                        })
                )
                .then()
                .doOnSuccess(v -> log.debug("Deactivated {} roles for user {}", rolesToRemove.size(), dbUser.getId()));
    }

    /**
     * Create user with race condition protection.
     */
    private Mono<User> createUserWithRaceProtection(SchoolFeeUser jwtUser) {
        log.info("Creating user record for Keycloak ID: {}", jwtUser.getUserId());

        User newUser = User.builder()
                .id(UUID.randomUUID())
                .keycloakId(jwtUser.getUserId())
                .schoolId(jwtUser.getSchoolId())
                .email(jwtUser.getEmail())
                .phone(normalizePhoneSafely(jwtUser.getPhoneNumber()))
                .firstName(jwtUser.getFirstName())
                .lastName(jwtUser.getLastName())
                .userType(jwtUser.getUserType())
                .isActive(true)
                .lastLogin(ZonedDateTime.now())
                .build();

        return userRepository.save(newUser)
                .doOnSuccess(u -> log.info("Created user: id={}, type={}", u.getId(), u.getUserType()))
                .onErrorResume(DuplicateKeyException.class, e -> {
                    log.info("Race condition — user already created by concurrent request");
                    return Mono.delay(Duration.ofMillis(200))
                            .then(userRepository.findByKeycloakIdAndDeletedAtIsNull(jwtUser.getUserId()))
                            .switchIfEmpty(Mono.error(new SchoolFeeException(
                                    "INTERNAL_ERROR",
                                    "Failed to find or create user record")));
                });
    }

    /**
     * Enrich with guardian/children data.
     */
    private Mono<UserProfileResponse> enrichWithGuardianData(User dbUser, SchoolFeeUser jwtUser) {
        if (jwtUser.isParent()) {
            return guardianLinkingService.getOrLinkGuardian(dbUser, jwtUser)
                    .map(children -> buildProfileResponse(jwtUser, dbUser, children));
        }
        return Mono.just(buildProfileResponse(jwtUser, dbUser, Collections.emptyList()));
    }

    private String normalizePhoneSafely(String phoneNumber) {
        try {
            return PhoneNumberNormalizer.normalize(phoneNumber);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid phone number received: {}", phoneNumber);
            return null;
        }
    }

    /**
     * Build profile response.
     */
    private UserProfileResponse buildProfileResponse(
            SchoolFeeUser jwtUser, User dbUser,
            List<UserProfileResponse.ChildInfo> children) {

        return UserProfileResponse.builder()
                .userId(dbUser.getId())
                .keycloakId(jwtUser.getUserId())
                .email(jwtUser.getEmail())
                .phoneNumber(jwtUser.getPhoneNumber())
                .firstName(jwtUser.getFirstName())
                .lastName(jwtUser.getLastName())
                .userType(jwtUser.getUserType())
                .schoolId(jwtUser.getSchoolId())
                .schoolName(jwtUser.getSchoolName())
                .roles(jwtUser.getRoles())
                .children(children)
                .lastLogin(dbUser.getLastLogin())
                .isActive(dbUser.getIsActive())
                .build();
    }

}