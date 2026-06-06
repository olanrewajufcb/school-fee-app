package com.fee.app.schoolfeeapp.auth.service;

import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import reactor.core.publisher.Mono;

public interface AuthService {

    Mono<UserProfileResponse> getCurrentUserProfile();
}
