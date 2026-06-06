package com.fee.app.schoolfeeapp.auth.controller;

import com.fee.app.schoolfeeapp.auth.dto.request.BulkInvitationRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.BulkInvitationResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.GuardianInvitationResponse;
import com.fee.app.schoolfeeapp.auth.service.GuardianInvitationService;
import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class GuardianInvitationController {

    private final GuardianInvitationService guardianInvitationService;

    // ========================================================================
    // GUARDIAN INVITATION
    // ========================================================================

    /**
     * POST /api/v1/guardians/{guardianId}/invite
     * Send an invitation SMS to a guardian to create their account.
     *
     * The guardian record already exists (created during student enrollment).
     * This sends an SMS with a link to verify their phone number and set up their account.
     */
    @PostMapping("/guardians/{guardianId}/invite")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<GuardianInvitationResponse>>> inviteGuardian(
            @PathVariable UUID guardianId) {
        return guardianInvitationService.inviteGuardian(guardianId)
                .map(response ->
                        ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * POST /api/v1/guardians/invite-bulk
     * Send invitation SMS to multiple guardians at once.
     * Used when onboarding a new class or after bulk student enrollment.
     */
    @PostMapping("/guardians/invite-bulk")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<BulkInvitationResponse>>> inviteGuardiansBulk(
            @Valid @RequestBody BulkInvitationRequest request) {
        return guardianInvitationService.inviteGuardiansBulk(request)
                .map(response ->
                        ResponseEntity.ok(ApiResponse.success(response)));
    }
}
