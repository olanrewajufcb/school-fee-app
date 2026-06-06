package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.Exam;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ExamRepository extends ReactiveCrudRepository<Exam, UUID> {
    Flux<Exam> findByTermId(UUID termId);
    Mono<Exam> findByIdAndSchoolId(UUID id, UUID schoolId);
    Mono<Exam> findByIdAndSchoolIdAndTermId(UUID id, UUID schoolId, UUID termId);
}
