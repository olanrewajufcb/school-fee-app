package com.fee.app.schoolfeeapp.fee.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.dto.request.CreateFeeStructureRequest;
import com.fee.app.schoolfeeapp.fee.dto.response.CreateFeeStructureResponse;
import com.fee.app.schoolfeeapp.fee.dto.response.FeeAssignmentResponse;
import com.fee.app.schoolfeeapp.fee.dto.response.FeeDashboardResponse;
import com.fee.app.schoolfeeapp.fee.dto.response.FeeStructureResponse;
import com.fee.app.schoolfeeapp.fee.dto.response.StudentFeeResponse;
import com.fee.app.schoolfeeapp.fee.service.FeeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeControllerTest {

    @Mock
    private FeeService feeService;

    @InjectMocks
    private FeeController feeController;

    private static final UUID STRUCTURE_ID = UUID.fromString("b8901234-5678-9012-3456-789012345678");
    private static final UUID SESSION_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID TERM_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");
    private static final UUID CLASS_ID = UUID.fromString("f6a7b890-1234-4567-f123-456789012345");

    @Test
    @DisplayName("Should create fee structure successfully")
    void shouldCreateFeeStructureSuccessfully() {
        CreateFeeStructureRequest request = validCreateRequest();
        CreateFeeStructureResponse serviceResponse = new CreateFeeStructureResponse(
                STRUCTURE_ID,
                "Primary 1 Tuition",
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(10000),
                1,
                2,
                dueDate(),
                "ACTIVE",
                Instant.now());
        when(feeService.createFeeStructure(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(feeController.createFeeStructure(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<CreateFeeStructureResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(feeService).createFeeStructure(request);
    }

    @Test
    @DisplayName("Should propagate create fee structure error")
    void shouldPropagateCreateFeeStructureError() {
        CreateFeeStructureRequest request = validCreateRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "DUPLICATE_FEE_STRUCTURE",
                "An active fee structure with this name already exists for the term",
                "name");
        when(feeService.createFeeStructure(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(feeController.createFeeStructure(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(feeService).createFeeStructure(request);
    }

    @Test
    @DisplayName("Should assign fees successfully")
    void shouldAssignFeesSuccessfully() {
        FeeAssignmentResponse serviceResponse = new FeeAssignmentResponse(
                STRUCTURE_ID,
                2,
                BigDecimal.valueOf(20000),
                "ASSIGNED",
                dueDate().minusDays(3),
                "Fees assigned to 2 students.");
        when(feeService.assignFeesToStudents(STRUCTURE_ID)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(feeController.assignFees(STRUCTURE_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    ApiResponse<FeeAssignmentResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(feeService).assignFeesToStudents(STRUCTURE_ID);
    }

    @Test
    @DisplayName("Should propagate assign fees error")
    void shouldPropagateAssignFeesError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "STRUCTURE_NOT_FOUND",
                "Fee structure not found or does not belong to your school");
        when(feeService.assignFeesToStudents(STRUCTURE_ID)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(feeController.assignFees(STRUCTURE_ID))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(feeService).assignFeesToStudents(STRUCTURE_ID);
    }

    @Test
    @DisplayName("Should get fee structures successfully")
    void shouldGetFeeStructuresSuccessfully() {
        FeeStructureResponse serviceResponse = new FeeStructureResponse(
                STRUCTURE_ID,
                "Primary 1 Tuition",
                "First Term",
                "2025/2026",
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(10000),
                List.of("Primary 1"),
                1,
                2,
                50.0,
                dueDate(),
                "ACTIVE",
                null,
                Instant.now());
        when(feeService.getFeeStructures("ACTIVE", "current"))
                .thenReturn(Mono.just(List.of(serviceResponse)));

        StepVerifier.create(feeController.getFeeStructures("ACTIVE", "current"))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<FeeStructureResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).containsExactly(serviceResponse);
                })
                .verifyComplete();

        verify(feeService).getFeeStructures("ACTIVE", "current");
    }

    @Test
    @DisplayName("Should propagate get fee structures error")
    void shouldPropagateGetFeeStructuresError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "INVALID_STATUS",
                "Fee structure status must be ACTIVE or INACTIVE",
                "status");
        when(feeService.getFeeStructures("BAD", null)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(feeController.getFeeStructures("BAD", null))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(feeService).getFeeStructures("BAD", null);
    }

    @Test
    @DisplayName("Should get student fees successfully")
    void shouldGetStudentFeesSuccessfully() {
        UUID studentId = UUID.randomUUID();
        UUID studentFeeId = UUID.randomUUID();
        StudentFeeResponse serviceResponse = new StudentFeeResponse(
                studentFeeId,
                "Primary 1 Tuition",
                "First Term",
                true,
                false,
                List.of(new StudentFeeResponse.FeeItemDetail(
                        "Tuition",
                        BigDecimal.valueOf(10000),
                        true)),
                BigDecimal.valueOf(10000),
                BigDecimal.ZERO,
                BigDecimal.valueOf(4000),
                BigDecimal.valueOf(6000),
                dueDate(),
                30,
                "PARTIAL",
                false,
                BigDecimal.ZERO,
                null);
        when(feeService.getStudentFees(studentId)).thenReturn(Mono.just(List.of(serviceResponse)));

        StepVerifier.create(feeController.getStudentFees(studentId))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<StudentFeeResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).containsExactly(serviceResponse);
                })
                .verifyComplete();

        verify(feeService).getStudentFees(studentId);
    }

    @Test
    @DisplayName("Should propagate get student fees error")
    void shouldPropagateGetStudentFeesError() {
        UUID studentId = UUID.randomUUID();
        SchoolFeeException expectedError = new SchoolFeeException(
                "ACCESS_DENIED",
                "You do not have access to this student's fees",
                "studentId");
        when(feeService.getStudentFees(studentId)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(feeController.getStudentFees(studentId))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(feeService).getStudentFees(studentId);
    }

    @Test
    @DisplayName("Should get dashboard successfully")
    void shouldGetDashboardSuccessfully() {
        FeeDashboardResponse serviceResponse = new FeeDashboardResponse(
                "First Term",
                new FeeDashboardResponse.DashboardSummary(
                        BigDecimal.valueOf(30000),
                        BigDecimal.valueOf(15000),
                        BigDecimal.valueOf(15000),
                        50.0,
                        1,
                        1,
                        1),
                List.of(new FeeDashboardResponse.ClassCollection(
                        CLASS_ID.toString(),
                        "Primary 1",
                        2,
                        BigDecimal.valueOf(30000),
                        BigDecimal.valueOf(15000),
                        50.0)),
                new FeeDashboardResponse.UpcomingDeadlines(
                        new FeeDashboardResponse.DeadlineInfo(1, BigDecimal.valueOf(5000)),
                        new FeeDashboardResponse.DeadlineInfo(0, BigDecimal.ZERO),
                        new FeeDashboardResponse.DeadlineInfo(2, BigDecimal.valueOf(10000))),
                List.of(new FeeDashboardResponse.DailyTrend(
                        "2026-06-04",
                        BigDecimal.valueOf(15000),
                        2)));
        when(feeService.getFeeDashboard("current")).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(feeController.getDashboard("current"))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<FeeDashboardResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getData()).isEqualTo(serviceResponse);
                })
                .verifyComplete();

        verify(feeService).getFeeDashboard("current");
    }

    @Test
    @DisplayName("Should propagate dashboard error")
    void shouldPropagateDashboardError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "TERM_NOT_FOUND",
                "Current term not found",
                "termId");
        when(feeService.getFeeDashboard("current")).thenReturn(Mono.error(expectedError));

        StepVerifier.create(feeController.getDashboard("current"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(feeService).getFeeDashboard("current");
    }

    private CreateFeeStructureRequest validCreateRequest() {
        return new CreateFeeStructureRequest(
                "Primary 1 Tuition",
                SESSION_ID,
                TERM_ID,
                List.of(CLASS_ID),
                dueDate(),
                List.of(new CreateFeeStructureRequest.FeeItemRequest(
                        null,
                        "Tuition",
                        BigDecimal.valueOf(10000),
                        true,
                        1)),
                null);
    }

    private LocalDate dueDate() {
        return LocalDate.now().plusDays(30);
    }
}
