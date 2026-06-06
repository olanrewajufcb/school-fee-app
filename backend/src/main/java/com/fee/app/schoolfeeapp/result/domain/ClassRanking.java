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
@Table(name = "class_rankings", schema = "result")
public class ClassRanking {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("student_id") private UUID studentId;
    @Column("class_id") private UUID classId;
    @Column("term_id") private UUID termId;
    @Column("total_score") private BigDecimal totalScore;
    @Column("total_max_score") private int totalMaxScore;
    @Column("average_percentage") private BigDecimal averagePercentage;
    @Column("overall_grade") private String overallGrade;
    @Column("class_position") private int classPosition;
    @Column("out_of") private int outOf;
    @Column("subjects_taken") private int subjectsTaken;
    @Column("subjects_passed") private int subjectsPassed;
    @Column("computed_at") private Instant computedAt;
}