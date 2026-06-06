package com.fee.app.schoolfeeapp.payment.domain;

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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment_allocations", schema = "payment")
public class PaymentAllocation {

    @Id
    private UUID id;

    @Column("payment_id")
    private UUID paymentId;

    @Column("school_id")
    private UUID schoolId;

    @Column("student_fee_id")
    private UUID studentFeeId;

    private BigDecimal amount;

    @Column("created_at")
    private Instant createdAt;
}
