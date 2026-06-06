package com.fee.app.schoolfeeapp.school.dto.response;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record AcademicSessionResponse(
        UUID sessionId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        boolean isCurrent,
        List<TermResponse> terms
) {
    public record TermResponse(
            UUID termId,
            String name,
            int termNumber,
            LocalDate startDate,
            LocalDate endDate,
            boolean isCurrent
    ) {}
}