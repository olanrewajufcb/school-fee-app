package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record SubjectLookupResponse(
        UUID id,
        String name,
        String code
) {}
