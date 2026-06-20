package com.fee.app.schoolfeeapp.fee.service;

import com.fee.app.schoolfeeapp.fee.dto.request.CreateFeeStructureRequest;
import com.fee.app.schoolfeeapp.fee.dto.response.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public interface FeeService {

    Mono<CreateFeeStructureResponse> createFeeStructure(CreateFeeStructureRequest request);
    Mono<List<FeeStructureResponse>> getFeeStructures(String status, String termId);
    Mono<FeeAssignmentResponse> assignFeesToStudents(UUID structureId);
    Mono<List<StudentFeeResponse>> getStudentFees(UUID studentId);
    Mono<FeeDashboardResponse> getFeeDashboard(String termId);
    Mono<List<UUID>> getOutstandingFeeIds(String termId, String filter);
}