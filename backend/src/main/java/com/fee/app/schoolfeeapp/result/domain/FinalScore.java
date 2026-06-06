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
@Table(name = "final_scores", schema = "result")
public class FinalScore {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("student_id") private UUID studentId;
    @Column("subject_id") private UUID subjectId;
    @Column("class_id") private UUID classId;
    @Column("term_id") private UUID termId;
    @Column("ca_total") private BigDecimal caTotal;
    @Column("ca_max_total") private int caMaxTotal;
    @Column("exam_score") private BigDecimal examScore;
    @Column("exam_max_score") private int examMaxScore;
    @Column("final_score") private BigDecimal finalScore;
    private String grade;
    private String remark;
    private BigDecimal points;
    @Column("subject_position") private Integer subjectPosition;
    @Column("computed_at") private Instant computedAt;
}