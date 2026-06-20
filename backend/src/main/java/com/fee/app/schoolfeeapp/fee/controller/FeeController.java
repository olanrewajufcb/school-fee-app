package com.fee.app.schoolfeeapp.fee.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.fee.dto.request.CreateFeeStructureRequest;
import com.fee.app.schoolfeeapp.fee.dto.response.*;
import com.fee.app.schoolfeeapp.fee.service.FeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fees")
@RequiredArgsConstructor
public class FeeController {

    private final FeeService feeService;

    @PostMapping("/structures")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<CreateFeeStructureResponse>>> createFeeStructure(
            @Valid @RequestBody CreateFeeStructureRequest request) {
        return feeService.createFeeStructure(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

    @GetMapping("/structures")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<FeeStructureResponse>>>> getFeeStructures(
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String termId) {
        return feeService.getFeeStructures(status, termId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PostMapping("/structures/{structureId}/assign")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<FeeAssignmentResponse>>> assignFees(
            @PathVariable UUID structureId) {
        return feeService.assignFeesToStudents(structureId)
                .map(response -> ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.success(response)));
    }

    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<ApiResponse<List<StudentFeeResponse>>>> getStudentFees(
            @PathVariable UUID studentId) {
        return feeService.getStudentFees(studentId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<FeeDashboardResponse>>> getDashboard(
            @RequestParam(required = false, defaultValue = "current") String termId) {
        return feeService.getFeeDashboard(termId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @GetMapping("/outstanding-ids")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<List<UUID>>>> getOutstandingFeeIds(
            @RequestParam(required = false, defaultValue = "current") String termId,
            @RequestParam String filter) {
        return feeService.getOutstandingFeeIds(termId, filter)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }
}