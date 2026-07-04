package com.fee.app.schoolfeeapp.result.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentTraitAssessmentValueView {
    private UUID studentId;
    private UUID traitId;
    private String rating;
    private String comment;
}
