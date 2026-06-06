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
@Table("school.academic_sessions")
public class AcademicSession {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    @Column("is_current")
    private Boolean isCurrent;

    private String status;

    @Column("closed_at")
    private Instant closedAt;

    @Column("closed_by")
    private UUID closedBy;

    @Column("closed_notes")
    private String closedNotes;

    @Column("created_at")
    private Instant createdAt;
    @Column("updated_at")
    private Instant updatedAt;
    @Column("deleted_at")
    private Instant deletedAt;
    @Column("deleted_by")
    private UUID deletedBy;
    @Column("created_by")
    private UUID createdBy;
    @Column("updated_by")
    private UUID updatedBy;

    @Version
    @Column("version")
    private Integer version;

}
