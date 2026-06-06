package com.fee.app.schoolfeeapp.fee.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fee_structure_classes", schema = "fee")
public class FeeStructureClass {

    @Id
    private UUID id;

    @Column("fee_structure_id")
    private UUID feeStructureId;

    @Column("class_id")
    private UUID classId;

    @Column("effective_date")
    private LocalDate effectiveDate;

    @Column("expires_at")
    private LocalDate expiresAt;

    @Column("created_at")
    private Instant createdAt;
}