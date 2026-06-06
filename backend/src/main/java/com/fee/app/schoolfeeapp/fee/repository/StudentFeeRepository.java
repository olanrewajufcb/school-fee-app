package com.fee.app.schoolfeeapp.fee.repository;

import com.fee.app.schoolfeeapp.fee.domain.StudentFee;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface StudentFeeRepository extends ReactiveCrudRepository<StudentFee, UUID> {
    Flux<StudentFee> findByStudentIdAndSchoolId(UUID studentId, UUID schoolId);

    Mono<StudentFee> findByStudentIdAndFeeStructureId(UUID studentId, UUID feeStructureId);

    @Query("""
        SELECT *
        FROM fee.student_fees
        WHERE id = :id
          AND school_id = :schoolId
        FOR UPDATE
        """)
    Mono<StudentFee> findByIdAndSchoolIdForUpdate(UUID id, UUID schoolId);
}
