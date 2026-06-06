package com.fee.app.schoolfeeapp.auth.service;

import com.fee.app.schoolfeeapp.auth.dto.request.CreateParentRequest;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateParentResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateStaffResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.UserSummaryResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;


public interface UserManagementService {

    Mono<CreateParentResponse> createParent(CreateParentRequest request);

    Mono<CreateStaffResponse> createStaff(CreateStaffRequest request);

    Mono<PageResponse<UserSummaryResponse>> listUsers(String userType, String status, String search,
                                                      Pageable pageable, String requestId);
}
