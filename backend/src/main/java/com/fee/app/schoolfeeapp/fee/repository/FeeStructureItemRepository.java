package com.fee.app.schoolfeeapp.fee.repository;

import com.fee.app.schoolfeeapp.fee.domain.FeeStructureItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface FeeStructureItemRepository extends ReactiveCrudRepository<FeeStructureItem, UUID> {

    Flux<FeeStructureItem> findByFeeStructureId(UUID feeStructureId);

    Flux<FeeStructureItem> findByFeeStructureIdOrderBySortOrderAsc(UUID feeStructureId);
}