package com.fee.app.schoolfeeapp.school.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.school.dto.request.CreateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.request.PromoteStudentsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.*;
import com.fee.app.schoolfeeapp.school.service.ClassService;
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
@RequestMapping("/api/v1/schools/current/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    /**
     * POST /api/v1/schools/current/classes
     * Create a new class in the current school.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<ClassResponse>>> createClass(
            @Valid @RequestBody CreateClassRequest request) {
        return classService.createClass(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/schools/current/classes
     * List classes with optional filters.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<ClassResponse>>>> listClasses(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String gradeLevel,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status) {
        return classService.listClasses(sessionId, gradeLevel, status)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/schools/current/classes/{classId}
     * Get class details including student list and statistics.
     */
    @GetMapping("/{classId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<ClassDetailResponse>>> getClassDetails(
            @PathVariable UUID classId) {
        return classService.getClassDetails(classId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * PUT /api/v1/schools/current/classes/{classId}
     * Update class details.
     */
    @PutMapping("/{classId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UpdateClassResponse>>> updateClass(
            @PathVariable UUID classId,
            @Valid @RequestBody UpdateClassRequest request) {
        return classService.updateClass(classId, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * DELETE /api/v1/schools/current/classes/{classId}
     * Deactivate a class (soft delete).
     * Only allowed if no students are enrolled.
     */
    @DeleteMapping("/{classId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deactivateClass(
            @PathVariable UUID classId) {
        return classService.deactivateClass(classId)
                .thenReturn(ResponseEntity.ok(ApiResponse.success(null)));
    }

    /**
     * POST /api/v1/schools/current/classes/promote
     * Promote students from one class to another.
     */
    @PostMapping("/promote")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PromoteStudentsResponse>>> promoteStudents(
            @Valid @RequestBody PromoteStudentsRequest request) {
        return classService.promoteStudents(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }
}