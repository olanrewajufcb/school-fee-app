package com.fee.app.schoolfeeapp.student.service;

import com.fee.app.schoolfeeapp.student.dto.request.CreateAttendanceSessionRequest;
import com.fee.app.schoolfeeapp.student.dto.request.MarkAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.response.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AttendanceService {

    // Session management
    Mono<AttendanceSessionResponse> createSession(CreateAttendanceSessionRequest request);
    Mono<List<AttendanceSessionResponse>> getSessions(UUID classId, LocalDate date);

    // Marking attendance
    Mono<List<AttendanceResponse>> getSessionMarks(UUID sessionId);
    Mono<List<AttendanceResponse>> markAttendance(UUID sessionId, MarkAttendanceRequest request);
    Mono<AttendanceResponse> updateAttendanceMark(UUID markId, UpdateAttendanceRequest request);

    // Viewing attendance
    Mono<TodayAttendanceResponse> getTodayClassAttendance(UUID classId);
    Mono<List<AttendanceResponse>> getStudentAttendance(UUID studentId, UUID termId);
    Mono<AttendanceSummaryResponse> getStudentAttendanceSummary(UUID studentId, UUID termId);

    // Parent view
    Mono<List<ParentAttendanceResponse>> getMyChildrenTodayAttendance();
    Mono<List<ParentAttendanceResponse>> getMyChildrenAttendance(UUID studentId, LocalDate date);
}