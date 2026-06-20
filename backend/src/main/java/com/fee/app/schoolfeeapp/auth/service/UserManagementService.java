package com.fee.app.schoolfeeapp.auth.service;

import com.fee.app.schoolfeeapp.auth.dto.request.*;
import com.fee.app.schoolfeeapp.auth.dto.response.*;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

import java.util.Map;


public interface UserManagementService {

    Mono<CreateParentResponse> createParent(CreateParentRequest request);

    Mono<CreateStaffResponse> createStaff(CreateStaffRequest request);

    Mono<PageResponse<UserSummaryResponse>> listUsers(String userType, String status, String search,
                                                      Pageable pageable, String requestId);

    // Parent self-onboarding
    Mono<CheckAccountResponse> checkAccount(CheckAccountRequest request);
    Mono<Void> sendOtp(SendOtpRequest request);
    Mono<Map<String, String>> verifyOtpAndCreateAccount(VerifyOtpRequest request);
    Mono<Map<String, String>> setPassword(SetPasswordRequest request);
}
