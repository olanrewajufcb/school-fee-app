package com.fee.app.schoolfeeapp.school.dto.request;

import java.time.LocalDate;
import java.util.List;

public record UpdateSessionRequest(
        String name,
        LocalDate startDate,
        LocalDate endDate,
        List<TermUpdate> terms
) {
    public record TermUpdate(
            java.util.UUID termId,
            String name,
            LocalDate startDate,
            LocalDate endDate
    ) {}
}