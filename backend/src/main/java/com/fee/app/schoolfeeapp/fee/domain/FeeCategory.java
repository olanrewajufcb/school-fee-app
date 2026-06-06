package com.fee.app.schoolfeeapp.fee.domain;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table("fee.fee_categories")
public class FeeCategory {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    private String name;
    private String description;

    @Column("is_recurring")
    private Boolean isRecurring;

    @Column("is_optional")
    private Boolean isOptional;

    @Column("created_at")
    private Instant createdAt;

    }
