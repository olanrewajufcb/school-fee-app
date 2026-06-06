package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.GradeConfig;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface GradeConfigRepository extends ReactiveCrudRepository<GradeConfig, UUID> {
    Mono<GradeConfig> findBySchoolId(UUID schoolId);
    Mono<GradeConfig> findBySchoolIdAndIsActiveTrue(UUID schoolId);
}