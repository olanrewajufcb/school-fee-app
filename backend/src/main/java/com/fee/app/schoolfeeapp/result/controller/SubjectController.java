package com.fee.app.schoolfeeapp.result.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.result.dto.request.AssignSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.request.CreateSubjectRequest;
import com.fee.app.schoolfeeapp.result.dto.response.ClassSubjectResponse;
import com.fee.app.schoolfeeapp.result.dto.response.SubjectResponse;
import com.fee.app.schoolfeeapp.result.service.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
@Slf4j
public class SubjectController {

    private final SubjectService subjectService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SubjectResponse>>> createSubject(
            @Valid @RequestBody CreateSubjectRequest request) {
        log.info("Creating subject {}",  request);
        return subjectService.createSubject(request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'ACCOUNTANT')")
    public Mono<ResponseEntity<ApiResponse<List<SubjectResponse>>>> listSubjects() {
        return subjectService.listSubjects()
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @PutMapping("/{subjectId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<SubjectResponse>>> updateSubject(
            @PathVariable UUID subjectId,
            @Valid @RequestBody CreateSubjectRequest request) {
        return subjectService.updateSubject(subjectId, request)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @DeleteMapping("/{subjectId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> deactivateSubject(
            @PathVariable UUID subjectId) {
        return subjectService.deactivateSubject(subjectId)
                .thenReturn(ResponseEntity.ok(ApiResponse.success(null)));
    }

    @PostMapping("/class/{classId}/assign")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<ClassSubjectResponse>>> assignSubjectToClass(
            @PathVariable UUID classId,
            @Valid @RequestBody AssignSubjectRequest request) {
        return subjectService.assignSubjectToClass(classId, request)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(r)));
    }

    @GetMapping("/class/{classId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<ClassSubjectResponse>>>> getSubjectsForClass(
            @PathVariable UUID classId) {
        return subjectService.getSubjectsForClass(classId)
                .map(r -> ResponseEntity.ok(ApiResponse.success(r)));
    }

    @DeleteMapping("/class/{classId}/subject/{subjectId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<Void>>> removeSubjectFromClass(
            @PathVariable UUID classId,
            @PathVariable UUID subjectId) {
        return subjectService.removeSubjectFromClass(classId, subjectId)
                .thenReturn(ResponseEntity.ok(ApiResponse.success(null)));
    }
}