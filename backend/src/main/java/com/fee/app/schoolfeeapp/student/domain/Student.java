package com.fee.app.schoolfeeapp.student.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Data
@Table("school.students")
public class Student {

  @Id
  private UUID id;

  @Column("school_id")
  private UUID schoolId;

  @Column("admission_number")
  private String admissionNumber;

  @Column("first_name")
  private String firstName;

  @Column("middle_name")
  private String middleName;

  @Column("last_name")
  private String lastName;

  @Column("date_of_birth")
  private LocalDate dateOfBirth;

  private String gender;

  @Column("current_class_id")
  private UUID currentClassId;

  @Column("enrollment_date")
  private LocalDate enrollmentDate;

  @Column("enrollment_status")
  private String enrollmentStatus;

  @Column("medical_notes")
  private String medicalNotes;

  @Column("profile_photo_url")
  private String profilePhotoUrl;

  @Column("created_at")
  private Instant createdAt;

  @Column("updated_at")
  private Instant updatedAt;

  @Column("updated_by")
  private UUID updatedBy;

  private Instant deletedAt;

  @Version
  private Integer version;


}
