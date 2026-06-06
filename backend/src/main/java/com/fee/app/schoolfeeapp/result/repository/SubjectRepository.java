package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.Subject;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface SubjectRepository extends ReactiveCrudRepository<Subject, UUID> {
    Flux<Subject> findBySchoolIdAndIsActiveTrue(UUID schoolId);
    Mono<Subject> findByIdAndSchoolIdAndIsActiveTrue(UUID id, UUID schoolId);
}
