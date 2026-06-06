package com.fee.app.schoolfeeapp.auth.dto.response;

import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.User;

public record UserGuardianResult(User user, StudentGuardian guardian) {}
