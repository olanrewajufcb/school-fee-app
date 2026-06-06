package com.fee.app.schoolfeeapp.fee.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@Table("fee.fee_discounts")
public class FeeDiscount {
    
    @Id
    private UUID id;
    
    @Column("school_id")
    private UUID schoolId;
    
    private String name;
    private String description;
    
    @Column("discount_type")
    private String discountType;
    
    @Column("discount_value")
    private BigDecimal discountValue;
    
    @Column("is_active")
    private Boolean isActive;
    
    @Column("created_at")
    private Instant createdAt;

    }