package com.fee.app.schoolfeeapp.fee.repository;

import com.fee.app.schoolfeeapp.fee.domain.FeeStructureClass;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface FeeStructureClassRepository extends ReactiveCrudRepository<FeeStructureClass, UUID> {

    @Query("""
        SELECT fee_structure_id, class_id, effective_date, expires_at, created_at
        FROM fee.fee_structure_classes
        WHERE fee_structure_id = :feeStructureId
          AND deleted_at IS NULL
        """)
    Flux<FeeStructureClass> findByFeeStructureId(UUID feeStructureId);

    @Query("""
        SELECT fee_structure_id, class_id, effective_date, expires_at, created_at
        FROM fee.fee_structure_classes
        WHERE class_id = :classId
          AND deleted_at IS NULL
        """)
    Flux<FeeStructureClass> findByClassId(UUID classId);

    @Modifying
    @Query("""
        INSERT INTO fee.fee_structure_classes (fee_structure_id, class_id)
        VALUES (:feeStructureId, :classId)
        ON CONFLICT (fee_structure_id, class_id) DO NOTHING
        """)
    Mono<Integer> insertLink(UUID feeStructureId, UUID classId);
}
