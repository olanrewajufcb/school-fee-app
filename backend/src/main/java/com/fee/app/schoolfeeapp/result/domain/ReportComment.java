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
@Table(name = "report_comments", schema = "result")
public class ReportComment {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    @Column("student_id") private UUID studentId;
    @Column("term_id") private UUID termId;
    @Column("teacher_comment") private String teacherComment;
    @Column("teacher_id") private UUID teacherId;
    @Column("principal_comment") private String principalComment;
    @Column("principal_id") private UUID principalId;
    @Column("attendance_days_open") private Integer attendanceDaysOpen;
    @Column("attendance_days_present") private Integer attendanceDaysPresent;
    @Column("attendance_days_absent") private Integer attendanceDaysAbsent;
    @Column("next_term_resumes") private LocalDate nextTermResumes;
    @Column("next_term_fees") private BigDecimal nextTermFees;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}