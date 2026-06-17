package com.fee.app.schoolfeeapp.auth.service.impl;


import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.dto.request.BulkInvitationRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.BulkInvitationResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.GuardianInvitationResponse;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.service.GuardianInvitationService;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuardianInvitationServiceImpl implements GuardianInvitationService {

    private final StudentGuardianRepository guardianRepository;
    private final SmsService smsService;

    @Value("${app.frontend-url:https://schoolfee.app}")
    private String frontendUrl;

    /**
     * Send invitation SMS to a single guardian.
     * The SMS contains a link for the guardian to verify their phone
     * and create their Keycloak account.
     */
    public Mono<GuardianInvitationResponse> inviteGuardian(UUID guardianId) {
        return guardianRepository.findById(guardianId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "GUARDIAN_NOT_FOUND",
                        "Guardian not found: " + guardianId)))
                .flatMap(guardian -> {
                    if (guardian.getUserId() != null) {
                        return Mono.just(
                        new GuardianInvitationResponse(
                                guardian.getId(),
                                guardian.getFirstName() + " " + guardian.getLastName(),
                                guardian.getPhone(),
                                false,
                                null,
                                "Guardian already has an account linked"));
                    }
                    
                    String invitationToken = generateInvitationToken(guardian);
                    String message = buildInvitationMessage(guardian, invitationToken);
                    
                    return smsService.send(guardian.getPhone(), message)
                            .thenReturn(new GuardianInvitationResponse(
                            guardian.getId(),
                            guardian.getFirstName() + " " + guardian.getLastName(),
                            guardian.getPhone(),
                            true,
                            invitationToken,
                            "Invitation SMS sent to " + guardian.getPhone()));
                });
    }

    /**
     * Send invitation SMS to multiple guardians.
     */
    public Mono<BulkInvitationResponse> inviteGuardiansBulk(BulkInvitationRequest request) {
        return Flux.fromIterable(request.guardianIds())
                .flatMap(guardianId -> inviteGuardian(guardianId)
                        .map(result -> new BulkInvitationResponse.InvitationResult(
                                guardianId,
                                result.phoneNumber(),
                                result.invitationSent(),
                                result.message()
                        ))
                        .onErrorResume(error -> Mono.just(
                                new BulkInvitationResponse.InvitationResult(
                                        guardianId,
                                        null,
                                        false,
                                        error.getMessage()
                                )
                        ))
                )
                .collectList()
                .map(results ->
        new BulkInvitationResponse(request.guardianIds().size(),
                (int) results.stream()
                        .filter(BulkInvitationResponse.InvitationResult::success).count(),
                (int) results.stream()
                        .filter(r -> !r.success()).count(),
                results));
    }

    private String generateInvitationToken(StudentGuardian guardian) {
        // Encode guardian ID + phone in the token
        String raw = guardian.getId() + ":" + guardian.getPhone();
        return Base64.getUrlEncoder().encodeToString(raw.getBytes()).substring(0, 12);
    }

    private String buildInvitationMessage(StudentGuardian guardian, String token) {
        String schoolName = "Your School"; // Phase 2: Fetch school name
        
        return String.format(
                "Welcome %s! %s invites you to SchoolFee. " +
                "Create your account to view fees and results: " +
                "%s/join/%s",
                guardian.getFirstName(),
                schoolName,
                frontendUrl,
                token
        );
    }
}