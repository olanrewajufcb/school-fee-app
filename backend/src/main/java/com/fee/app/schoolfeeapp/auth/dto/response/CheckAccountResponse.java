package com.fee.app.schoolfeeapp.auth.dto.response;

import java.util.List;
import java.util.UUID;

public record CheckAccountResponse(
        boolean found,
        String schoolName,
        String guardianName,
        int childrenCount,
        String message,
        List<SchoolOption> options  // NEW: for multiple matches
) {
    public CheckAccountResponse(boolean found, String schoolName, String guardianName,
                                 int childrenCount, String message) {
        this(found, schoolName, guardianName, childrenCount, message, null);
    }

    public record SchoolOption(
            UUID schoolId,
            String schoolName,
            String guardianName
    ) {}
}