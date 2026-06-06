package com.fee.app.schoolfeeapp.result.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "scores", schema = "result")
public class ResultScore {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("exam_id") private UUID examId;
    @Column("student_id") private UUID studentId;
    @Column("subject_id") private UUID subjectId;
    @Column("class_id") private UUID classId;
    @Column("term_id") private UUID termId;
    private BigDecimal score;
    @Column("max_score") private int maxScore;
    private String grade;
    private String remark;
    private BigDecimal points;
    @Column("recorded_by") private UUID recordedBy;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}