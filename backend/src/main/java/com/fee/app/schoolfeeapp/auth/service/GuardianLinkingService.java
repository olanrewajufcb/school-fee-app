package com.fee.app.schoolfeeapp.auth.service;

import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import reactor.core.publisher.Mono;

import java.util.List;

public interface GuardianLinkingService {

     Mono<List<UserProfileResponse.ChildInfo>> getOrLinkGuardian(
            User dbUser, SchoolFeeUser jwtUser);
}
