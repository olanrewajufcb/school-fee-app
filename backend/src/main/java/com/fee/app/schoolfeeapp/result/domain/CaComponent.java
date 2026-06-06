package com.fee.app.schoolfeeapp.result.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "ca_components", schema = "result")
public class CaComponent {
    @Id private UUID id;
    @Column("school_id") private UUID schoolId;
    private String name;
    @Column("max_score") private int maxScore;
    @Column("weight_percentage") private BigDecimal weightPercentage;
    @Column("sort_order") private int sortOrder;
    @Column("is_active") private boolean isActive;
    @Column("created_at") private Instant createdAt;
}