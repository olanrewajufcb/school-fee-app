package com.fee.app.schoolfeeapp.auth.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Builder
@Data
@Table(name = "student_guardian_links", schema = "school")
public class StudentGuardianLink {

    @Id
    private UUID id;

    @Column("guardian_id")
    private UUID guardianId;

    @Column("student_id")
    private UUID studentId;

    @Column("school_id")
    private UUID schoolId;

    // Relationship
    @Column("relationship")
    private String relationship;
    // MOTHER, FATHER, GUARDIAN, UNCLE, AUNT, GRANDMOTHER, GRANDFATHER, OTHER

    // Permissions
    @Column("is_primary_contact")
    private Boolean isPrimaryContact;

    @Column("can_pick_up_child")
    private Boolean canPickUpChild;

    @Column("can_view_fees")
    private Boolean canViewFees;

    @Column("can_view_results")
    private Boolean canViewResults;

    @Column("can_view_attendance")
    private Boolean canViewAttendance;

    @Column("can_receive_sms")
    private Boolean canReceiveSms;

    // Contact priority (1 = first to contact)
    @Column("contact_priority")
    private Integer contactPriority;

    @CreatedDate
    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private UUID createdBy;

    @LastModifiedDate
    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("updated_by")
    private UUID updatedBy;

    @Column("deleted_at")
    private OffsetDateTime deletedAt;

    @Column("deleted_by")
    private UUID deletedBy;

    @Version
    @Column("version")
    private Integer version;
}