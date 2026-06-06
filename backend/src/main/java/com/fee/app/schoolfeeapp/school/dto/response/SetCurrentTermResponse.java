package com.fee.app.schoolfeeapp.school.dto.response;


import java.time.LocalDate;
import java.util.UUID;

public record SetCurrentTermResponse(
        UUID termId,
        String name,
        String sessionName,
        boolean isCurrent,
        LocalDate startDate,
        LocalDate endDate,
        PreviousTerm previousCurrentTerm
) {
    public record PreviousTerm(
            UUID termId,
            String name,
            String status
    ) {}
}