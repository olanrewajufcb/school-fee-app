package com.fee.app.schoolfeeapp.student.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.student.dto.request.CreateAttendanceSessionRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateAttendanceRequest;
import com.fee.app.schoolfeeapp.student.dto.response.AttendanceSessionResponse;
import com.fee.app.schoolfeeapp.student.dto.response.AttendanceResponse;
import com.fee.app.schoolfeeapp.student.dto.response.AttendanceSummaryResponse;
import com.fee.app.schoolfeeapp.student.dto.response.ParentAttendanceResponse;
import com.fee.app.schoolfeeapp.student.dto.response.TodayAttendanceResponse;
import com.fee.app.schoolfeeapp.student.service.AttendanceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceControllerTest {

    @Mock
    private AttendanceService attendanceService;

    @InjectMocks
    private AttendanceController attendanceController;

    private static final UUID SESSION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CLASS_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID TERM_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID STUDENT_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID ATTENDANCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");

    @Test
    @DisplayName("Should create session successfully")
    void shouldCreateSessionSuccessfully() {
        CreateAttendanceSessionRequest request = new CreateAttendanceSessionRequest(
                CLASS_ID, TERM_ID, LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL");
        AttendanceSessionResponse serviceResponse = new AttendanceSessionResponse(
                SESSION_ID, CLASS_ID, "Primary 1A", TERM_ID, "Term 1", request.date(), request.sessionType(), false, 0, 0, 0, 0, 0);

        when(attendanceService.createSession(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(attendanceController.createSession(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<AttendanceSessionResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).createSession(request);
    }

    @Test
    @DisplayName("Should get sessions successfully")
    void shouldGetSessionsSuccessfully() {
        LocalDate date = LocalDate.of(2026, 6, 18);
        AttendanceSessionResponse sessionResponse = new AttendanceSessionResponse(
                SESSION_ID, CLASS_ID, "Primary 1A", TERM_ID, "Term 1", date, "MORNING_ARRIVAL", false, 0, 0, 0, 0, 0);

        when(attendanceService.getSessions(CLASS_ID, date)).thenReturn(Mono.just(List.of(sessionResponse)));

        StepVerifier.create(attendanceController.getSessions(CLASS_ID, date))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<AttendanceSessionResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0)).isEqualTo(sessionResponse);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).getSessions(CLASS_ID, date);
    }

    @Test
    @DisplayName("Should get session marks successfully")
    void shouldGetSessionMarksSuccessfully() {
        AttendanceResponse mark = new AttendanceResponse(
                ATTENDANCE_ID, STUDENT_ID, "Tolu Adebayo", "STU260001", "PRESENT",
                LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL", java.time.LocalTime.of(7, 45),
                "Parent", null, null, null, null, null);

        when(attendanceService.getSessionMarks(SESSION_ID)).thenReturn(Mono.just(List.of(mark)));

        StepVerifier.create(attendanceController.getSessionMarks(SESSION_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<AttendanceResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0)).isEqualTo(mark);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).getSessionMarks(SESSION_ID);
    }

    @Test
    @DisplayName("Should propagate error when session creation fails")
    void shouldPropagateErrorWhenSessionCreationFails() {
        CreateAttendanceSessionRequest request = new CreateAttendanceSessionRequest(
                CLASS_ID, TERM_ID, LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL");
        SchoolFeeException expectedError = new SchoolFeeException(
                "CLASS_NOT_FOUND", "Class not found", "classId");

        when(attendanceService.createSession(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(attendanceController.createSession(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(attendanceService, times(1)).createSession(request);
    }

    @Test
    @DisplayName("Should update attendance mark successfully")
    void shouldUpdateMarkSuccessfully() {
        UpdateAttendanceRequest request = new UpdateAttendanceRequest(
                "LATE", "08:15", "Father", null, null, null, null, "Excused"
        );
        AttendanceResponse serviceResponse = new AttendanceResponse(
                ATTENDANCE_ID, STUDENT_ID, "Tolu Adebayo", "STU260001", "LATE",
                LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL", java.time.LocalTime.of(8, 15),
                "Father", null, null, null, null, "Excused"
        );

        when(attendanceService.updateAttendanceMark(ATTENDANCE_ID, request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(attendanceController.updateMark(ATTENDANCE_ID, request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<AttendanceResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).updateAttendanceMark(ATTENDANCE_ID, request);
    }

    @Test
    @DisplayName("Should get student attendance history successfully")
    void shouldGetStudentAttendanceSuccessfully() {
        AttendanceResponse record = new AttendanceResponse(
                ATTENDANCE_ID, STUDENT_ID, "Tolu Adebayo", "STU260001", "PRESENT",
                LocalDate.of(2026, 6, 18), "MORNING_ARRIVAL", java.time.LocalTime.of(7, 45),
                "Parent", null, null, null, null, null
        );

        when(attendanceService.getStudentAttendance(STUDENT_ID, TERM_ID)).thenReturn(Mono.just(List.of(record)));

        StepVerifier.create(attendanceController.getStudentAttendance(STUDENT_ID, TERM_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<AttendanceResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0)).isEqualTo(record);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).getStudentAttendance(STUDENT_ID, TERM_ID);
    }

    @Test
    @DisplayName("Should get student attendance summary successfully")
    void shouldGetStudentAttendanceSummarySuccessfully() {
        AttendanceSummaryResponse summaryResponse = new AttendanceSummaryResponse(
                10, 8, 1, 1, 0, 80.0
        );

        when(attendanceService.getStudentAttendanceSummary(STUDENT_ID, TERM_ID)).thenReturn(Mono.just(summaryResponse));

        StepVerifier.create(attendanceController.getStudentAttendanceSummary(STUDENT_ID, TERM_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<AttendanceSummaryResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(summaryResponse);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).getStudentAttendanceSummary(STUDENT_ID, TERM_ID);
    }

    @Test
    @DisplayName("Should get today's attendance for parent's children successfully")
    void shouldGetMyChildrenAttendanceSuccessfully() {
        ParentAttendanceResponse childResponse = new ParentAttendanceResponse(
                STUDENT_ID, "Tolu Adebayo", "Primary 1A", LocalDate.of(2026, 6, 18),
                java.time.LocalTime.of(7, 45), "Parent", "ON_TIME", null, null, null, null, null
        );

        when(attendanceService.getMyChildrenTodayAttendance()).thenReturn(Mono.just(List.of(childResponse)));

        StepVerifier.create(attendanceController.getMyChildrenAttendance())
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<ParentAttendanceResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0)).isEqualTo(childResponse);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).getMyChildrenTodayAttendance();
    }

    @Test
    @DisplayName("Should get attendance for specific child and date successfully")
    void shouldGetMyChildAttendanceSuccessfully() {
        LocalDate date = LocalDate.of(2026, 6, 18);
        ParentAttendanceResponse childResponse = new ParentAttendanceResponse(
                STUDENT_ID, "Tolu Adebayo", "Primary 1A", date,
                java.time.LocalTime.of(7, 45), "Parent", "ON_TIME", null, null, null, null, null
        );

        when(attendanceService.getMyChildrenAttendance(STUDENT_ID, date)).thenReturn(Mono.just(List.of(childResponse)));

        StepVerifier.create(attendanceController.getMyChildAttendance(STUDENT_ID, date))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<ParentAttendanceResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).hasSize(1);
                    assertThat(body.getData().get(0)).isEqualTo(childResponse);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).getMyChildrenAttendance(STUDENT_ID, date);
    }

    @Test
    @DisplayName("Should get today's class attendance successfully")
    void shouldGetTodayClassAttendanceSuccessfully() {
        TodayAttendanceResponse responseDto = new TodayAttendanceResponse(
                CLASS_ID, "Primary 1A", LocalDate.now().toString(),
                10, 8, 1, 1, 1, List.of()
        );

        when(attendanceService.getTodayClassAttendance(CLASS_ID)).thenReturn(Mono.just(responseDto));

        StepVerifier.create(attendanceController.getTodayClassAttendance(CLASS_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<TodayAttendanceResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(responseDto);
                })
                .verifyComplete();

        verify(attendanceService, times(1)).getTodayClassAttendance(CLASS_ID);
    }
}
