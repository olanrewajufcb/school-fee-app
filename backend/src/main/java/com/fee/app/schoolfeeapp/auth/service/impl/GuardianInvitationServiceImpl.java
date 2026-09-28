package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.dto.request.BulkInvitationRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.BulkInvitationResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.GuardianInvitationResponse;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.service.GuardianInvitationService;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.service.EmailService;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
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
    private final EmailService emailService;
    private final SchoolRepository schoolRepository;

    @Value("${app.frontend-url:https://schoolfee.app}")
    private String frontendUrl;

    /**
     * Send invitation email to a single guardian.
     * The email contains a secure registration link for the guardian to create
     * their Parent Portal account.
     */
    @Override
    public Mono<GuardianInvitationResponse> inviteGuardian(UUID guardianId) {
        return guardianRepository.findById(guardianId)
                .switchIfEmpty(Mono.error(new SchoolFeeException(
                        "GUARDIAN_NOT_FOUND",
                        "Guardian not found: " + guardianId)))
                .flatMap(guardian -> {
                    String guardianFullName = ((guardian.getFirstName() != null ? guardian.getFirstName() : "") + " " +
                            (guardian.getLastName() != null ? guardian.getLastName() : "")).trim();

                    if (guardian.getUserId() != null) {
                        return Mono.just(
                                new GuardianInvitationResponse(
                                        guardian.getId(),
                                        guardianFullName,
                                        guardian.getPhone(),
                                        guardian.getEmail(),
                                        false,
                                        null,
                                        "Guardian already has an account linked"
                                )
                        );
                    }

                    if (guardian.getEmail() == null || guardian.getEmail().isBlank()) {
                        return Mono.error(new SchoolFeeException(
                                "GUARDIAN_EMAIL_REQUIRED",
                                "Guardian " + guardianFullName + " does not have an email address registered. Please add an email address to send the invitation."
                        ));
                    }

                    String invitationToken = generateInvitationToken(guardian);
                    String invitationLink = String.format("%s/join/%s", frontendUrl, invitationToken);

                    Mono<String> schoolNameMono = guardian.getSchoolId() != null
                            ? schoolRepository.findById(guardian.getSchoolId())
                                    .map(school -> school.getName() != null && !school.getName().isBlank() ? school.getName() : "Your School")
                                    .defaultIfEmpty("Your School")
                            : Mono.just("Your School");

                    return schoolNameMono.flatMap(schoolName ->
                            emailService.sendGuardianInvitationEmail(
                                    guardian.getEmail(),
                                    guardianFullName,
                                    schoolName,
                                    invitationLink
                            ).thenReturn(new GuardianInvitationResponse(
                                    guardian.getId(),
                                    guardianFullName,
                                    guardian.getPhone(),
                                    guardian.getEmail(),
                                    true,
                                    invitationToken,
                                    "Invitation email sent to " + guardian.getEmail()
                            ))
                    );
                });
    }

    /**
     * Send invitation email to multiple guardians.
     */
    @Override
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
        String identifier = guardian.getEmail() != null && !guardian.getEmail().isBlank()
                ? guardian.getEmail()
                : (guardian.getPhone() != null ? guardian.getPhone() : "user");
        String raw = guardian.getId() + ":" + identifier;
        return Base64.getUrlEncoder().encodeToString(raw.getBytes()).substring(0, 12);
    }
}