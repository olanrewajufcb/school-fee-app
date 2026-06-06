package com.fee.app.schoolfeeapp.school.dto.request;

public record CloseSessionRequest(
        boolean completeAllTerms,
        boolean archiveStudentRecords,
        String notes
) {}