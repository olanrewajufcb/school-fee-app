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
@Table(name = "ca_scores", schema = "result")
public class CaScore {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("student_id") private UUID studentId;
    @Column("subject_id") private UUID subjectId;
    @Column("class_id") private UUID classId;
    @Column("term_id") private UUID termId;
    @Column("ca_component_id") private UUID caComponentId;
    private BigDecimal score;
    @Column("max_score") private int maxScore;
    @Column("recorded_by") private UUID recordedBy;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}