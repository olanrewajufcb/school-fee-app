package com.fee.app.schoolfeeapp.result.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student_trait_assessments", schema = "result")
public class StudentTraitAssessment {
    @Id
    private UUID id;
    @Column("school_id")
    private UUID schoolId;
    @Column("student_id")
    private UUID studentId;
    @Column("class_id")
    private UUID classId;
    @Column("term_id")
    private UUID termId;
    @Column("trait_id")
    private UUID traitId;
    private String rating;
    private String comment;
    @Column("recorded_by")
    private UUID recordedBy;
    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;
}
