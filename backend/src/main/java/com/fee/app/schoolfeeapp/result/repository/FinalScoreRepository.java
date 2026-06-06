package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.FinalScore;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface FinalScoreRepository extends ReactiveCrudRepository<FinalScore, UUID> {
    Flux<FinalScore> findByStudentIdAndTermIdOrderBySubjectId(UUID studentId, UUID termId);
    Flux<FinalScore> findByStudentIdAndTermIdAndSchoolIdOrderBySubjectId(
            UUID studentId, UUID termId, UUID schoolId);
    Flux<FinalScore> findByClassIdAndTermIdOrderByFinalScoreDesc(UUID classId, UUID termId);

    @Query("""
        DELETE FROM result.final_scores
        WHERE class_id = :classId AND term_id = :termId AND subject_id = :subjectId
        """)
    Mono<Void> deleteByClassIdAndTermIdAndSubjectId(UUID classId, UUID termId, UUID subjectId);
}
