package com.fee.app.schoolfeeapp.school.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@Table("school.terms")
public class Term {
    
    @Id
    private UUID id;
    
    @Column("session_id")
    private UUID sessionId;
    
    private String name;
    
    @Column("term_number")
    private Short termNumber;
    
    @Column("start_date")
    private LocalDate startDate;
    
    @Column("end_date")
    private LocalDate endDate;
    
    @Column("is_current")
    private Boolean isCurrent;

    private String status;

    @Column("completed_at")
    private Instant completedAt;

    @Column("completed_by")
    private UUID completedBy;
    
    @Column("created_at")
    private Instant createdAt;

    @Version
    @Column("version")
    private Integer version;

    }
