package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.ScoreAuditLog;
import org.springframework.data.repository.query.Param;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ScoreAuditLogRepository extends ReactiveCrudRepository<ScoreAuditLog, UUID> {
    @Query("""
            INSERT INTO result.score_audit_log (
                id,
                school_id,
                score_type,
                score_id,
                student_id,
                subject_id,
                term_id,
                old_score,
                new_score,
                changed_by,
                changed_at,
                reason
            )
            VALUES (
                :#{#audit.id},
                :#{#audit.schoolId},
                :#{#audit.scoreType},
                :#{#audit.scoreId},
                :#{#audit.studentId},
                :#{#audit.subjectId},
                :#{#audit.termId},
                :#{#audit.oldScore},
                :#{#audit.newScore},
                :#{#audit.changedBy},
                :#{#audit.changedAt},
                :#{#audit.reason}
            )
            RETURNING *
            """)
    Mono<ScoreAuditLog> insert(@Param("audit") ScoreAuditLog audit);

    Flux<ScoreAuditLog> findByScoreIdOrderByChangedAtDesc(UUID scoreId);
}
