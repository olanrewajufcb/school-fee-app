package com.fee.app.schoolfeeapp.fee.repository;

import com.fee.app.schoolfeeapp.fee.domain.LedgerEntry;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface LedgerEntryRepository extends ReactiveCrudRepository<LedgerEntry, UUID> {

    /**
     * Find all ledger entries for a student fee, ordered by creation date.
     */
    @Query("""
        SELECT *
        FROM fee.ledger_entries
        WHERE student_fee_id = :studentFeeId
          AND deleted_at IS NULL
          AND COALESCE(is_reversed, false) = false
        ORDER BY created_at ASC
        """)
    Flux<LedgerEntry> findByStudentFeeIdOrderByCreatedAtAsc(UUID studentFeeId);

    /**
     * Find the most recent ledger entry for a student fee (for balance calculation).
     */
    @Query("""
        SELECT *
        FROM fee.ledger_entries
        WHERE student_fee_id = :studentFeeId
          AND deleted_at IS NULL
          AND COALESCE(is_reversed, false) = false
        ORDER BY created_at DESC
        LIMIT 1
        """)
    Mono<LedgerEntry> findTopByStudentFeeIdOrderByCreatedAtDesc(UUID studentFeeId);

    /**
     * Find ledger entries by source entity (e.g., payment, discount).
     */
    Flux<LedgerEntry> findBySourceEntityTypeAndSourceEntityId(String sourceEntityType, UUID sourceEntityId);

    /**
     * Count entries by student fee ID for status derivation.
     */
    Mono<Long> countByStudentFeeIdAndEntryType(UUID studentFeeId, String entryType);
}
