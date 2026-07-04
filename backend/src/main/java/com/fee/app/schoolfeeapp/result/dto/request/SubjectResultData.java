package com.fee.app.schoolfeeapp.result.dto.request;

import java.math.BigDecimal;

public record SubjectResultData(
            String subjectName,
            BigDecimal caTotal,
            int caMaxTotal,
            BigDecimal examScore,
            int examMaxScore,
            BigDecimal finalScore,
            String grade,
            String remark,
            Integer position
    ) {}