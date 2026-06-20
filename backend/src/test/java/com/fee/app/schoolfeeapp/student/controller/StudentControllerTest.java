package com.fee.app.schoolfeeapp.student.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.student.dto.request.EnrollStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.request.UpdateStudentRequest;
import com.fee.app.schoolfeeapp.student.dto.response.EnrollStudentResponse;
import com.fee.app.schoolfeeapp.student.dto.response.MyChildrenResponse;
import com.fee.app.schoolfeeapp.student.dto.response.StudentDetailResponse;
import com.fee.app.schoolfeeapp.student.dto.response.StudentListResponse;
import com.fee.app.schoolfeeapp.student.dto.response.UpdateStudentResponse;
import com.fee.app.schoolfeeapp.student.service.StudentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

    @Mock
    private StudentService studentService;

    @InjectMocks
    private StudentController studentController;

    private static final UUID STUDENT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID CLASS_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @Test
    @DisplayName("Should enroll student successfully")
    void shouldEnrollStudentSuccessfully() {
        EnrollStudentRequest request = validRequest();
        EnrollStudentResponse serviceResponse = new EnrollStudentResponse(
                STUDENT_ID,
                "STU260001",
                "Ada",
                "Lovelace",
                CLASS_ID,
                "Primary 1",
                true,
                null,
                "Student enrolled successfully with 1 guardian(s)");
        when(studentService.enrollStudent(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(studentController.enrollStudent(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<EnrollStudentResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(studentService, times(1)).enrollStudent(request);
    }

    @Test
    @DisplayName("Should propagate enroll student error")
    void shouldPropagateEnrollStudentError() {
        EnrollStudentRequest request = validRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "CLASS_FULL",
                "Class Primary 1 is full (30/30)",
                "classId");
        when(studentService.enrollStudent(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(studentController.enrollStudent(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(studentService, times(1)).enrollStudent(request);
    }

    @Test
    @DisplayName("Should list students successfully")
    void shouldListStudentsSuccessfully() {
        StudentListResponse student = new StudentListResponse(
                STUDENT_ID,
                "STU260001ABCD",
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                new StudentListResponse.CurrentClass(CLASS_ID, "Primary 1", "PRIMARY_1"),
                LocalDate.of(2025, 9, 8),
                "ACTIVE",
                "2348031234567",
                "Grace Hopper",
                null);
        PageResponse<StudentListResponse> serviceResponse =
                new PageResponse<>(List.of(student), 0, 20, 1, 1);
        when(studentService.listStudents(eq(CLASS_ID), eq("ALL"), eq("Ada"), any(Pageable.class)))
                .thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(studentController.listStudents(CLASS_ID, "ALL", "Ada", 0, 20))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<PageResponse<StudentListResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(studentService).listStudents(eq(CLASS_ID), eq("ALL"), eq("Ada"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should reject invalid list page before service call")
    void shouldRejectInvalidListPageBeforeServiceCall() {
        StepVerifier.create(studentController.listStudents(null, "ACTIVE", null, -1, 20))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_PAGE_REQUEST");
                    assertThat(exception.getField()).isEqualTo("page");
                })
                .verify();

        verify(studentService, never()).listStudents(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject invalid list size before service call")
    void shouldRejectInvalidListSizeBeforeServiceCall() {
        StepVerifier.create(studentController.listStudents(null, "ACTIVE", null, 0, 0))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    SchoolFeeException exception = (SchoolFeeException) error;
                    assertThat(exception.getErrorCode()).isEqualTo("INVALID_PAGE_REQUEST");
                    assertThat(exception.getField()).isEqualTo("size");
                })
                .verify();

        verify(studentService, never()).listStudents(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should propagate list students error")
    void shouldPropagateListStudentsError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "INVALID_STATUS",
                "Student status must be one of ACTIVE, INACTIVE, or ALL",
                "status");
        when(studentService.listStudents(eq(null), eq("ARCHIVED"), eq(null), any(Pageable.class)))
                .thenReturn(Mono.error(expectedError));

        StepVerifier.create(studentController.listStudents(null, "ARCHIVED", null, 0, 20))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();
    }

    @Test
    @DisplayName("Should get student details successfully")
    void shouldGetStudentDetailsSuccessfully() {
        StudentDetailResponse serviceResponse = studentDetailResponse();
        when(studentService.getStudentDetails(STUDENT_ID)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(studentController.getStudentDetails(STUDENT_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<StudentDetailResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(studentService).getStudentDetails(STUDENT_ID);
    }

    @Test
    @DisplayName("Should propagate get student details error")
    void shouldPropagateGetStudentDetailsError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "STUDENT_NOT_FOUND",
                "Student not found or does not belong to your school");
        when(studentService.getStudentDetails(STUDENT_ID)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(studentController.getStudentDetails(STUDENT_ID))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();
    }

    @Test
    @DisplayName("Should get my children successfully")
    void shouldGetMyChildrenSuccessfully() {
        MyChildrenResponse child = new MyChildrenResponse(
                STUDENT_ID,
                "STU260001ABCD",
                "Ada",
                "Lovelace",
                "Primary 1",
                null,
                new MyChildrenResponse.FeeStatus(
                        null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "PENDING", null));
        when(studentService.getMyChildren()).thenReturn(Mono.just(List.of(child)));

        StepVerifier.create(studentController.getMyChildren())
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<MyChildrenResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).containsExactly(child);
                })
                .verifyComplete();

        verify(studentService).getMyChildren();
    }

    @Test
    @DisplayName("Should propagate my children error")
    void shouldPropagateMyChildrenError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "ACCESS_DENIED",
                "Only parents can view their children");
        when(studentService.getMyChildren()).thenReturn(Mono.error(expectedError));

        StepVerifier.create(studentController.getMyChildren())
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();
    }

    @Test
    @DisplayName("Should update student successfully")
    void shouldUpdateStudentSuccessfully() {
        UpdateStudentRequest request = updateStudentRequest();
        UpdateStudentResponse serviceResponse = new UpdateStudentResponse(
                STUDENT_ID,
                "STU260001ABCD",
                "Marie",
                "Curie",
                CLASS_ID,
                "Primary 1",
                "ACTIVE",
                java.time.Instant.now());
        when(studentService.updateStudent(STUDENT_ID, request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(studentController.updateStudent(STUDENT_ID, request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<UpdateStudentResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(studentService).updateStudent(STUDENT_ID, request);
    }

    @Test
    @DisplayName("Should propagate update student error")
    void shouldPropagateUpdateStudentError() {
        UpdateStudentRequest request = updateStudentRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "CLASS_FULL",
                "Class Primary 1 is full (30/30)",
                "classId");
        when(studentService.updateStudent(STUDENT_ID, request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(studentController.updateStudent(STUDENT_ID, request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(studentService).updateStudent(STUDENT_ID, request);
    }

    private EnrollStudentRequest validRequest() {
        return new EnrollStudentRequest(
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                CLASS_ID,
                List.of(new EnrollStudentRequest.GuardianInfo(
                        "Grace",
                        "Hopper",
                        "08031234567",
                        "grace@example.com",
                        "Mother",
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        1)),
                null);
    }

    private UpdateStudentRequest updateStudentRequest() {
        return new UpdateStudentRequest(
                "Marie",
                null,
                "Curie",
                "FEMALE",
                LocalDate.of(2017, 2, 3),
                CLASS_ID,
                "ACTIVE",
                null);
    }

    private StudentDetailResponse studentDetailResponse() {
        return new StudentDetailResponse(
                STUDENT_ID,
                "STU260001ABCD",
                "Ada",
                "Lovelace",
                null,
                "FEMALE",
                LocalDate.of(2018, 1, 1),
                new StudentDetailResponse.CurrentClass(CLASS_ID, "Primary 1", "PRIMARY_1", null),
                LocalDate.of(2025, 9, 8),
                "ACTIVE",
                List.of(new StudentDetailResponse.ParentInfo(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Grace Hopper",
                        "2348031234567",
                        "MOTHER",
                        true)),
                new StudentDetailResponse.FeeSummary(
                        "First Term",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "PENDING",
                        null),
                new StudentDetailResponse.FeeSummary(
                        "Second Term",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "PENDING",
                        null),
                null,
                null);
    }
}
