package com.fee.app.schoolfeeapp.fee.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table("fee.student_discounts")
public class StudentDiscount {
    
    @Id
    private UUID id;
    
    @Column("student_fee_id")
    private UUID studentFeeId;
    
    @Column("discount_id")
    private UUID discountId;
    
    @Column("discount_amount")
    private BigDecimal discountAmount;
    
    @Column("applied_by")
    private UUID appliedBy;
    
    @Column("applied_at")
    private Instant appliedAt;
    
    private String reason;

    }