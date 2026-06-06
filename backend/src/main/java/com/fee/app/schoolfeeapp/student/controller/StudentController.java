package com.fee.app.schoolfeeapp.student.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.student.dto.request.EnrollStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.response.*;
import com.fee.app.schoolfeeapp.student.service.StudentService;
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
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * POST /api/v1/students
     * Enroll a new student with guardians.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<EnrollStudentResponse>>> enrollStudent(
            @Valid @RequestBody EnrollStudentRequest request) {
        return studentService.enrollStudent(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/students
     * List students with pagination and filters.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<PageResponse<StudentListResponse>>>> listStudents(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Mono.fromCallable(() -> validatedPageable(page, size))
                .flatMap(pageable -> studentService.listStudents(classId, status, search, pageable))
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/students/{studentId}
     * Get student details with guardians and fee summary.
     */
    @GetMapping("/{studentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'ACCOUNTANT', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<ApiResponse<StudentDetailResponse>>> getStudentDetails(
            @PathVariable UUID studentId) {
        return studentService.getStudentDetails(studentId)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * GET /api/v1/students/my-children
     * Get the current parent's children with fee status.
     */
    @GetMapping("/my-children")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<List<MyChildrenResponse>>>> getMyChildren() {
        return studentService.getMyChildren()
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    /**
     * PUT /api/v1/students/{studentId}
     * Update a student's information.
     */
    @PutMapping("/{studentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Mono<ResponseEntity<ApiResponse<UpdateStudentResponse>>> updateStudent(
            @PathVariable UUID studentId,
            @Valid @RequestBody UpdateStudentRequest request) {
        return studentService.updateStudent(studentId, request)
                .map(response -> ResponseEntity.ok(ApiResponse.success(response)));
    }

    private Pageable validatedPageable(int page, int size) {
        if (page < 0) {
            throw new SchoolFeeException(
                    "INVALID_PAGE_REQUEST",
                    "Page must be greater than or equal to 0",
                    "page");
        }
        if (size <= 0) {
            throw new SchoolFeeException(
                    "INVALID_PAGE_REQUEST",
                    "Size must be greater than 0",
                    "size");
        }
        if (size > 100) {
            throw new SchoolFeeException(
                    "INVALID_PAGE_REQUEST",
                    "Size must not exceed 100",
                    "size");
        }
        return PageRequest.of(page, size);
    }
}
