package com.fee.app.schoolfeeapp.fee.repository;

import com.fee.app.schoolfeeapp.fee.domain.FeeCategory;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface FeeCategoryRepository extends ReactiveCrudRepository<FeeCategory, UUID> {

    Mono<Boolean> existsBySchoolIdAndName(UUID schoolId, String name);

    Mono<Boolean> existsByIdAndSchoolId(UUID id, UUID schoolId);
}
