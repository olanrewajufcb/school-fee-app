package com.fee.app.schoolfeeapp.auth.service;

import com.fee.app.schoolfeeapp.auth.dto.request.BulkInvitationRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.BulkInvitationResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.GuardianInvitationResponse;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface GuardianInvitationService {

    Mono<GuardianInvitationResponse> inviteGuardian(UUID guardianId);

    Mono<BulkInvitationResponse> inviteGuardiansBulk(BulkInvitationRequest request);
}
