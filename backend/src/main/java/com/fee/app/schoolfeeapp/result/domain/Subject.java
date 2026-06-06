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

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "subjects", schema = "result")
public class Subject {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    private String name;
    private String code;
    private String category;
    @Column("is_active") private boolean isActive;
    @Column("created_at") private Instant createdAt;
}