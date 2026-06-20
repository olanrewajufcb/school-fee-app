package com.fee.app.schoolfeeapp.student.controller;


import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.student.dto.request.CreateAttendanceSessionRequest;
import com.fee.app.schoolfeeapp.student.dto.request.MarkAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.response.*;
import com.fee.app.schoolfeeapp.student.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * POST /api/v1/attendance/sessions
     * Create an attendance session for a class.
     */
    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<AttendanceSessionResponse>>> createSession(
            @Valid @RequestBody CreateAttendanceSessionRequest request) {
        return attendanceService.createSession(request)
                .map(res -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(res)));
    }

    /**
     * GET /api/v1/attendance/sessions
     * Get attendance sessions for a class.
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<AttendanceSessionResponse>>>> getSessions(
            @RequestParam UUID classId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.getSessions(classId, date)
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * GET /api/v1/attendance/sessions/{sessionId}/marks
     * Get attendance marks for a specific session.
     */
    @GetMapping("/sessions/{sessionId}/marks")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<AttendanceResponse>>>> getSessionMarks(
            @PathVariable UUID sessionId) {
        return attendanceService.getSessionMarks(sessionId)
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * POST /api/v1/attendance/sessions/{sessionId}/marks
     * Mark attendance for all students in a session.
     */
    @PostMapping("/sessions/{sessionId}/marks")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<List<AttendanceResponse>>>> markAttendance(
            @PathVariable UUID sessionId,
            @Valid @RequestBody MarkAttendanceRequest request) {
        return attendanceService.markAttendance(sessionId, request)
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * PUT /api/v1/attendance/marks/{markId}
     * Update an individual attendance record.
     */
    @PutMapping("/marks/{markId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<AttendanceResponse>>> updateMark(
            @PathVariable UUID markId,
            @Valid @RequestBody UpdateAttendanceRequest request) {
        return attendanceService.updateAttendanceMark(markId, request)
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * GET /api/v1/attendance/students/{studentId}
     * Get attendance history for a student.
     */
    @GetMapping("/students/{studentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<ApiResponse<List<AttendanceResponse>>>> getStudentAttendance(
            @PathVariable UUID studentId,
            @RequestParam UUID termId) {
        return attendanceService.getStudentAttendance(studentId, termId)
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * GET /api/v1/attendance/students/{studentId}/summary
     * Get attendance summary for a student.
     */
    @GetMapping("/students/{studentId}/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER', 'PARENT')")
    public Mono<ResponseEntity<ApiResponse<AttendanceSummaryResponse>>> getStudentAttendanceSummary(
            @PathVariable UUID studentId,
            @RequestParam UUID termId) {
        return attendanceService.getStudentAttendanceSummary(studentId, termId)
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * GET /api/v1/attendance/classes/{classId}/today
     * Get today's attendance for a class.
     */
    @GetMapping("/classes/{classId}/today")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Mono<ResponseEntity<ApiResponse<TodayAttendanceResponse>>> getTodayClassAttendance(
            @PathVariable UUID classId) {
        return attendanceService.getTodayClassAttendance(classId)
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * GET /api/v1/attendance/my-children
     * Get today's attendance for the current parent's children.
     */
    @GetMapping("/my-children")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<List<ParentAttendanceResponse>>>> getMyChildrenAttendance() {
        return attendanceService.getMyChildrenTodayAttendance()
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }

    /**
     * GET /api/v1/attendance/my-children/{studentId}
     * Get attendance for a specific child on a specific date.
     */
    @GetMapping("/my-children/{studentId}")
    @PreAuthorize("hasRole('PARENT')")
    public Mono<ResponseEntity<ApiResponse<List<ParentAttendanceResponse>>>> getMyChildAttendance(
            @PathVariable UUID studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return attendanceService.getMyChildrenAttendance(studentId, date != null ? date : LocalDate.now())
                .map(res -> ResponseEntity.ok(ApiResponse.success(res)));
    }
}