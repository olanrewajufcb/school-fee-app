package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.service.GuardianLinkingService;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.utils.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Handles linking guardian records to user accounts and fetching linked children.
 * Extracted from AuthService because this logic will grow:
 * - Invitation tokens (Phase 2)
 * - OTP verification flows (Phase 2)
 * - Guardian approval workflows (Phase 2)
 * - Guardian merging/unlinking (Phase 2)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GuardianLinkingServiceImpl implements GuardianLinkingService {

    private final StudentGuardianRepository guardianRepository;
    private final StudentGuardianLinkRepository guardianLinkRepository;

    /**
     * Get linked children for a parent user.
     * If this is a first login and the guardian record exists but isn't linked,
     * link it now (phone-based matching with OTP already verified by Keycloak).
     * SECURITY NOTE: Phone matching is acceptable here because:
     * - Keycloak already verified the phone via OTP during registration
     * - The user proved ownership of the phone number
     * - We only link if the guardian record has no existing user_id
     * Phase 2: Replace with invitation token flow for stronger security.
     */
    public Mono<List<UserProfileResponse.ChildInfo>> getOrLinkGuardian(
            User dbUser, SchoolFeeUser jwtUser) {
        
        // First, try to find an already-linked guardian
        return guardianRepository.findByUserIdAndDeletedAtIsNull(dbUser.getId())
                .flatMap(guardian -> fetchChildrenForGuardian(guardian.getId())
                .collectList())
                .switchIfEmpty(Mono.just(Collections.emptyList()))
                .flatMap(childInfos ->  {
                    if (childInfos.isEmpty()){
                        return tryPhoneMatchAndLink(dbUser, jwtUser.getPhoneNumber());
                    }
                    return Mono.just(childInfos);
                });
    }

    /**
     * Try to find an unlinked guardian record by phone number and link it.
     */
    private Mono<List<UserProfileResponse.ChildInfo>> tryPhoneMatchAndLink(
            User dbUser,
            String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return Mono.just(Collections.emptyList());
        }

        final String normalizedPhone;

        try {
            normalizedPhone = PhoneNumberNormalizer.normalize(phoneNumber);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid phone number format for user {}: {}",
                    dbUser.getId(), phoneNumber);

            return Mono.just(Collections.emptyList());
        }

        return guardianRepository
                .findByPhoneAndSchoolIdAndUserIdIsNullAndDeletedAtIsNull(
                        normalizedPhone,
                        dbUser.getSchoolId())
                .flatMap(guardian -> {
                    log.info("Linking guardian {} to user {}",
                            guardian.getId(),
                            dbUser.getId());

                    guardian.setUserId(dbUser.getId());

                    return guardianRepository.save(guardian);
                })
                .flatMapMany(guardian -> fetchChildrenForGuardian(guardian.getId()))
                .collectList();
    }

    /**
     * Fetch children linked to a guardian.
     * Filters out soft-deleted guardian links.
     */
    private Flux<UserProfileResponse.ChildInfo> fetchChildrenForGuardian(
            UUID guardianId) {
        
        return guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(guardianId)
                .map(link -> UserProfileResponse.ChildInfo.builder()
                        .studentId(link.getStudentId())
                        .guardianId(link.getGuardianId())
                        .relationship(link.getRelationship())
                        .canViewFees(link.getCanViewFees())
                        .canViewResults(link.getCanViewResults())
                        .canViewAttendance(link.getCanViewAttendance())
                        .build());
    }
}