package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.ClassRanking;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ClassRankingRepository extends ReactiveCrudRepository<ClassRanking, UUID> {
    Flux<ClassRanking> findByClassIdAndTermIdOrderByClassPosition(UUID classId, UUID termId);
    Mono<ClassRanking> findByStudentIdAndTermId(UUID studentId, UUID termId);
    Mono<ClassRanking> findByStudentIdAndTermIdAndSchoolId(UUID studentId, UUID termId, UUID schoolId);
}
