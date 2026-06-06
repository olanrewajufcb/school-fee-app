package com.fee.app.schoolfeeapp.school.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Data
@Table("school.student_class_history")
public class StudentClassHistory {
    
    @Id
    private UUID id;
    
    @Column("student_id")
    private UUID studentId;
    
    @Column("class_id")
    private UUID classId;
    
    @Column("term_id")
    private UUID termId;
    
    @Column("entry_date")
    private LocalDate entryDate;
    
    @Column("exit_date")
    private LocalDate exitDate;
    
    private String status;
    
    @Column("created_at")
    private Instant createdAt;

    }