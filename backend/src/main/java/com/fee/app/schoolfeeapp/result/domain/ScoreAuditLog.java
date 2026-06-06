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
@Table(name = "score_audit_log", schema = "result")
public class ScoreAuditLog {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("score_type") private String scoreType;
    @Column("score_id") private UUID scoreId;
    @Column("student_id") private UUID studentId;
    @Column("subject_id") private UUID subjectId;
    @Column("term_id") private UUID termId;
    @Column("old_score") private BigDecimal oldScore;
    @Column("new_score") private BigDecimal newScore;
    @Column("changed_by") private UUID changedBy;
    @Column("changed_at") private Instant changedAt;
    private String reason;
}