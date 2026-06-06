package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.ClassSubject;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ClassSubjectRepository extends ReactiveCrudRepository<ClassSubject, UUID> {
    Flux<ClassSubject> findByClassIdAndIsActiveTrue(UUID classId);
    Mono<ClassSubject> findByClassIdAndSubjectIdAndSchoolIdAndIsActiveTrue(
            UUID classId, UUID subjectId, UUID schoolId);
}
