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
import java.time.LocalDate;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "exams", schema = "result")
public class Exam {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("term_id") private UUID termId;
    private String name;
    @Column("exam_type") private String examType;
    @Column("exam_date") private LocalDate examDate;
    @Column("max_score") private int maxScore;
    @Column("weight_percentage") private BigDecimal weightPercentage;
    @Column("is_published") private boolean isPublished;
    @Column("created_by") private UUID createdBy;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}