package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record CaComponentLookupResponse(
        UUID id,
        String name,
        int maxScore
) {}
