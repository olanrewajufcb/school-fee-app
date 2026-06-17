package com.fee.app.schoolfeeapp.auth.dto.response;

import java.util.UUID;

public record KeycloakUserResult(UUID userId, String temporaryPassword) {}
