package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.PublishedResult;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PublishedResultRepository extends ReactiveCrudRepository<PublishedResult, UUID> {
    Mono<PublishedResult> findBySchoolIdAndTermId(UUID schoolId, UUID termId);
}