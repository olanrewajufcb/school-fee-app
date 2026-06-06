package com.fee.app.schoolfeeapp.payment.repository;

import com.fee.app.schoolfeeapp.payment.domain.Receipt;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface ReceiptRepository extends ReactiveCrudRepository<Receipt, UUID> {

    @Query("""
        SELECT *
        FROM payment.receipts
        WHERE payment_id = :paymentId
          AND deleted_at IS NULL
        """)
    Mono<Receipt> findByPaymentId(UUID paymentId);

    @Query("""
        SELECT *
        FROM payment.receipts
        WHERE receipt_number = :receiptNumber
          AND deleted_at IS NULL
        """)
    Mono<Receipt> findByReceiptNumber(String receiptNumber);
}
