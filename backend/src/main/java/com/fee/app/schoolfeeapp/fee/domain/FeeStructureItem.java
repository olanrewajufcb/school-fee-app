package com.fee.app.schoolfeeapp.fee.domain;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Builder
@Data
@Table("fee.fee_structure_items")
public class FeeStructureItem {
    
    @Id
    private UUID id;
    
    @Column("fee_structure_id")
    private UUID feeStructureId;
    
    @Column("fee_category_id")
    private UUID feeCategoryId;
    
    private String description;
    
    private BigDecimal amount;
    
    @Column("is_mandatory")
    private Boolean isMandatory;
    
    @Column("sort_order")
    private Integer sortOrder;

    }