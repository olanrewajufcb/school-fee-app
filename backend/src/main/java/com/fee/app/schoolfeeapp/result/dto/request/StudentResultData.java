package com.fee.app.schoolfeeapp.result.dto.request;

import java.util.List;

public record StudentResultData(
            String studentName,
            String admissionNumber,
            String className,
            String termName,
            List<SubjectResultData> subjects,
            String average,
            String overallGrade,
            String position,
            String subjectsPassed,
            String teacherComment,
            String principalComment
    ) {}

