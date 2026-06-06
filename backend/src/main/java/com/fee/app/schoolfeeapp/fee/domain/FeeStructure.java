package com.fee.app.schoolfeeapp.fee.domain;


import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("fee.fee_structures")
public class FeeStructure {

    @Id
    private UUID id;

    @Column("school_id")
    private UUID schoolId;

    private String name;

    @Column("academic_session_id")
    private UUID academicSessionId;

    @Column("term_id")
    private UUID termId;

    @Column("total_amount")
    private BigDecimal totalAmount;

    @Column("due_date")
    private LocalDate dueDate;

    @Column("late_fee_percentage")
    private BigDecimal lateFeePercentage;

    @Column("late_fee_flat_amount")
    private BigDecimal lateFeeFlatAmount;

    @Column("late_fee_applies_after_days")
    private Integer lateFeeAppliesAfterDays;

    private String status;

    @Column("created_at")
    private Instant createdAt;

    @Column("created_by")
    private UUID createdBy;

    @Version
    @Column("version")
    private Integer version;
    }
