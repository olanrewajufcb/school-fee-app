package com.fee.app.schoolfeeapp.payment.repository;

import com.fee.app.schoolfeeapp.payment.domain.PaymentAllocation;
import java.math.BigDecimal;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface PaymentAllocationRepository extends ReactiveCrudRepository<PaymentAllocation, UUID> {

    Flux<PaymentAllocation> findByPaymentId(UUID paymentId);

    @Query("""
        SELECT COALESCE(SUM(a.amount), 0)
        FROM payment.payment_allocations a
        JOIN payment.payments p ON p.id = a.payment_id
        WHERE a.student_fee_id = :studentFeeId
          AND a.school_id = :schoolId
          AND p.school_id = :schoolId
          AND p.status IN ('PENDING', 'PROCESSING')
          AND p.deleted_at IS NULL
        """)
    Mono<BigDecimal> sumActiveAllocatedAmount(UUID studentFeeId, UUID schoolId);
}
