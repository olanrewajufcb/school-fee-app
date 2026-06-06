package com.fee.app.schoolfeeapp.auth.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Builder
@Data
@Table(name = "student_guardians", schema = "school")
public class StudentGuardian {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    // Identity
    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("phone")
    private String phone;

    @Column("email")
    private String email;

    @Column("alternative_phone")
    private String alternativePhone;

    // Optional auth link
    @Column("user_id")
    private UUID userId;

    // Contact preferences
    @Column("preferred_contact_method")
    private String preferredContactMethod;  // SMS | EMAIL | BOTH

    @Column("preferred_language")
    private String preferredLanguage;         // en | yo | ha | ig

    // Status
    @Column("is_active")
    private Boolean isActive;

    @CreatedDate
    @Column("created_at")
    private Instant createdAt;

    @Column("created_by")
    private UUID createdBy;

    @LastModifiedDate
    @Column("updated_at")
    private Instant updatedAt;

    @Column("updated_by")
    private UUID updatedBy;

    @Column("deleted_at")
    private Instant deletedAt;

    @Column("deleted_by")
    private UUID deletedBy;
}
