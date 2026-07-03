package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record TraitAssessmentValueResponse(
        UUID studentId,
        UUID traitId,
        String rating,
        String comment
) {}
