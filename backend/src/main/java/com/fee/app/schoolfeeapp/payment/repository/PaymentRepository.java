package com.fee.app.schoolfeeapp.payment.repository;

import com.fee.app.schoolfeeapp.payment.domain.Payment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, UUID> {

    @Query("""
        SELECT *
        FROM payment.payments
        WHERE id = :id
          AND school_id = :schoolId
          AND deleted_at IS NULL
        """)
    Mono<Payment> findByIdAndSchoolId(UUID id, UUID schoolId);

    @Query("""
        SELECT *
        FROM payment.payments
        WHERE gateway_transaction_ref = :gatewayTransactionRef
          AND deleted_at IS NULL
        """)
    Mono<Payment> findByGatewayTransactionRef(String gatewayTransactionRef);

    @Query("""
        SELECT *
        FROM payment.payments
        WHERE gateway_transaction_ref = :gatewayTransactionRef
          AND deleted_at IS NULL
        FOR UPDATE
        """)
    Mono<Payment> findByGatewayTransactionRefForUpdate(String gatewayTransactionRef);

    @Query("""
        SELECT *
        FROM payment.payments
        WHERE idempotency_key = :idempotencyKey
          AND deleted_at IS NULL
        """)
    Mono<Payment> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT * FROM payment.payments
        WHERE paid_by = :userId AND school_id = :schoolId
          AND deleted_at IS NULL
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<Payment> findByPaidByAndSchoolIdOrderByCreatedAtDesc(
            UUID userId, UUID schoolId, int limit, long offset);

    @Query("""
        SELECT COUNT(*)
        FROM payment.payments
        WHERE paid_by = :userId AND school_id = :schoolId
          AND deleted_at IS NULL
        """)
    Mono<Long> countByPaidByAndSchoolId(UUID userId, UUID schoolId);

    @Query("""
        SELECT * FROM payment.payments
        WHERE student_id = :studentId AND school_id = :schoolId
          AND deleted_at IS NULL
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<Payment> findByStudentIdAndSchoolIdOrderByCreatedAtDesc(
            UUID studentId, UUID schoolId, int limit, long offset);

    @Query("""
        SELECT COUNT(*)
        FROM payment.payments
        WHERE student_id = :studentId AND school_id = :schoolId
          AND deleted_at IS NULL
        """)
    Mono<Long> countByStudentIdAndSchoolId(UUID studentId, UUID schoolId);

    /**
     * Find payments by school, date range, and status.
     */
    @Query("""
    SELECT * FROM payment.payments
    WHERE school_id = :schoolId
      AND created_at BETWEEN :startDate AND :endDate
      AND (:status IS NULL OR status = :status)
      AND deleted_at IS NULL
    ORDER BY created_at DESC
    """)
    Flux<Payment> findBySchoolIdAndCreatedAtBetweenAndStatus(
            UUID schoolId, Instant startDate, Instant endDate, String status);

    /**
     * Find fee collection payments for a school/term with optional class and status filters.
     */
    @Query("""
    SELECT p.*
    FROM payment.payments p
    JOIN fee.student_fees sf
      ON sf.id = p.student_fee_id
     AND sf.school_id = :schoolId
    JOIN fee.fee_structures fs
      ON fs.id = sf.fee_structure_id
     AND fs.school_id = :schoolId
     AND fs.term_id = :termId
     AND fs.deleted_at IS NULL
    JOIN school.students s
      ON s.id = p.student_id
     AND s.school_id = :schoolId
     AND s.deleted_at IS NULL
    WHERE p.school_id = :schoolId
      AND p.deleted_at IS NULL
      AND (:classId IS NULL OR s.current_class_id = :classId)
      AND (:status IS NULL OR p.status = :status)
    ORDER BY p.created_at DESC
    """)
    Flux<Payment> findFeeCollectionReportPayments(
            UUID schoolId, UUID termId, UUID classId, String status);
}
