package com.fee.app.schoolfeeapp.result.repository;

import com.fee.app.schoolfeeapp.result.domain.ReportComment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface ReportCommentRepository extends ReactiveCrudRepository<ReportComment, UUID> {
    Mono<ReportComment> findByStudentIdAndTermId(UUID studentId, UUID termId);
    Mono<ReportComment> findByStudentIdAndTermIdAndSchoolId(UUID studentId, UUID termId, UUID schoolId);
}
