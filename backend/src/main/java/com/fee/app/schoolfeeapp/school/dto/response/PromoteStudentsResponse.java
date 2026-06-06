package com.fee.app.schoolfeeapp.school.dto.response;


import java.util.List;
import java.util.UUID;

public record PromoteStudentsResponse(
        UUID promotionId,
        String fromClass,
        String toClass,
        int studentsPromoted,
        List<FailedPromotion> failedPromotions,
        String message
) {
    public record FailedPromotion(
            UUID studentId,
            String reason
    ) {}
}