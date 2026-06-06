package com.fee.app.schoolfeeapp.school.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@Table("school.classes")
public class ClassEntity {
    
    @Id
    private UUID id;
    
    @Column("school_id")
    private UUID schoolId;
    
    private String name;
    
    @Column("grade_level")
    private String gradeLevel;
    
    private String section;
    
    @Column("academic_session_id")
    private UUID academicSessionId;
    
    @Column("class_teacher_id")
    private UUID classTeacherId;
    
    private Integer capacity;
    
    @Column("is_active")
    private Boolean isActive;
    
    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    @Column("updated_by")
    private UUID updatedBy;

    @Version
    @Column("version")
    private Integer version;

    }
