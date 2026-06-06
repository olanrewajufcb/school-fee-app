package com.fee.app.schoolfeeapp.auth.domain;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table(name = "users", schema = "auth")
public class User {

    @Id
    private UUID id;

    @Column("keycloak_id")
    private UUID keycloakId;

    private UUID schoolId;
    private String email;
    private String phone;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("user_type")
    private String userType;

    @Column("is_active")
    private Boolean isActive;

    @Column("last_login")
    private ZonedDateTime lastLogin;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    private Instant deletedAt;
}
