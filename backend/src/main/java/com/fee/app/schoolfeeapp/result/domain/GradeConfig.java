package com.fee.app.schoolfeeapp.result.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "grade_configs", schema = "result")
public class GradeConfig {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    private JsonNode config;
    @Column("ca_config") private JsonNode caConfig;
    @Column("is_active") private boolean isActive;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;
}