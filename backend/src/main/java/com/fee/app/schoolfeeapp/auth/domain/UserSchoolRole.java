package com.fee.app.schoolfeeapp.auth.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table(name = "user_school_roles", schema = "auth")
public class UserSchoolRole {

  @Id private UUID id;

  private UUID userId;

  private UUID schoolId;

  private String role;

  private Instant assignedAt;

  private UUID assignedBy;

  private Boolean isActive;
}