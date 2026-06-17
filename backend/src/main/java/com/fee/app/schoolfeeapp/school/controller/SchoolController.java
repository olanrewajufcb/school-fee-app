package com.fee.app.schoolfeeapp.school.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.school.dto.request.*;
import com.fee.app.schoolfeeapp.school.dto.response.*;
import com.fee.app.schoolfeeapp.school.service.SchoolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schools")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    /**
     * POST /api/v1/schools
     * Create a new school (Super Admin only).
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CreateSchoolResponse>>> createSchool(
            @Valid @RequestBody CreateSchoolRequest request) {
        return schoolService.createSchool(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }
    /**
     * GET /api/v1/schools/current
     * Get the current user's school profile.
     */
    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public Mono<ResponseEntity<ApiResponse<SchoolResponse>>> getCurrentSchool() {
        return schoolService.getCurrentSchool()
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/schools/{schoolId}
     * Get a specific school (Super Admin only).
     */
    @GetMapping("/{schoolId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SchoolResponse>>> getSchool(
            @PathVariable UUID schoolId) {
        return schoolService.getSchoolById(schoolId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * PUT /api/v1/schools/current
     * Update the current user's school.
     */
    @PutMapping("/current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SchoolResponse>>> updateSchool(
            @Valid @RequestBody UpdateSchoolRequest request) {
        return schoolService.updateSchool(request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * Patch / PUT /api/v1/schools/{schoolId}/deactivate
     * Deactivate a school (Super Admin only).
     */
    @RequestMapping(value = "/{schoolId}/deactivate", method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deactivateSchool(
            @PathVariable UUID schoolId) {
        return schoolService.deactivateSchool(schoolId)
                .thenReturn(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<SchoolSummaryResponse>>>> listSchools(
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return schoolService.listSchools(status, pageable)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    // ========================================================================
    // ACADEMIC SESSIONS
    // ========================================================================

    @GetMapping("/current/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<AcademicSessionResponse>>>> getSessions() {
        return schoolService.getCurrentSchoolSessions()
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    @PostMapping("/current/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AcademicSessionResponse>>> createSession(
            @Valid @RequestBody CreateAcademicSessionRequest request) {
        return schoolService.createSession(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

    @PutMapping("/current/sessions/{sessionId}/set-current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<AcademicSessionResponse>>> setCurrentSession(
            @PathVariable UUID sessionId) {
        return schoolService.setCurrentSession(sessionId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * PUT /api/v1/schools/current/sessions/{sessionId}
     * Update an academic session and optionally its terms.
     */
    @PutMapping("/current/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UpdateSessionResponse>>> updateSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody UpdateSessionRequest request) {
        return schoolService.updateSession(sessionId, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * PUT /api/v1/schools/current/sessions/{sessionId}/close
     * Close (complete) an academic session.
     */
    @PutMapping("/current/sessions/{sessionId}/close")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<CloseSessionResponse>>> closeSession(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CloseSessionRequest request) {
        return schoolService.closeSession(sessionId, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * PUT /api/v1/schools/current/sessions/current/terms/{termId}/set-current
     * Set a specific term as the current term within the current session.
     */
    @PutMapping("/current/sessions/current/terms/{termId}/set-current")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SetCurrentTermResponse>>> setCurrentTerm(
            @PathVariable UUID termId) {
        return schoolService.setCurrentTerm(termId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

}