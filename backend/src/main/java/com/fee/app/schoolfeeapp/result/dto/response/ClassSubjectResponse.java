package com.fee.app.schoolfeeapp.result.dto.response;

import java.util.UUID;

public record ClassSubjectResponse(
        UUID classSubjectId,
        UUID subjectId,
        String subjectName,
        String subjectCode,
        UUID teacherId,
        String teacherName
) {}