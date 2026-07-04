package com.fee.app.schoolfeeapp.result.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "assessment_traits", schema = "result")
public class AssessmentTrait {
    @Id
    private UUID id;
    @Column("school_id")
    private UUID schoolId;
    @Column("education_level")
    private String educationLevel;
    private String name;
    private String category;
    @Column("sort_order")
    private Integer sortOrder;
    @Column("is_active")
    private Boolean active;
    @Column("created_at")
    private Instant createdAt;
}
