package com.fee.app.schoolfeeapp.school.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.dto.request.CreateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.request.PromoteStudentsRequest;
import com.fee.app.schoolfeeapp.school.dto.response.ClassDetailResponse;
import com.fee.app.schoolfeeapp.school.dto.response.ClassResponse;
import com.fee.app.schoolfeeapp.school.dto.response.PromoteStudentsResponse;
import com.fee.app.schoolfeeapp.school.dto.response.UpdateClassRequest;
import com.fee.app.schoolfeeapp.school.dto.response.UpdateClassResponse;
import com.fee.app.schoolfeeapp.school.service.ClassService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassControllerTest {

    @Mock
    private ClassService classService;

    @InjectMocks
    private ClassController classController;

    private static final UUID CLASS_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID TARGET_CLASS_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891");
    private static final UUID SESSION_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID TEACHER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID OTHER_TEACHER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789013");
    private static final UUID STUDENT_ID_1 = UUID.fromString("d1111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_ID_2 = UUID.fromString("d2222222-2222-2222-2222-222222222222");

    @Test
    @DisplayName("Should create class successfully")
    void shouldCreateClassSuccessfully() {
        CreateClassRequest request = validRequest();
        ClassResponse serviceResponse = classResponse();
        when(classService.createClass(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(classController.createClass(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    ApiResponse<ClassResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();

                    ClassResponse data = body.getData();
                    assertThat(data.classId()).isEqualTo(CLASS_ID);
                    assertThat(data.name()).isEqualTo("Primary 1");
                    assertThat(data.gradeLevel()).isEqualTo("Grade 1");
                    assertThat(data.section()).isEqualTo("A");
                    assertThat(data.sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(data.capacity()).isEqualTo(35);
                    assertThat(data.status()).isEqualTo("ACTIVE");
                })
                .verifyComplete();

        verify(classService, times(1)).createClass(request);
    }

    @Test
    @DisplayName("Should propagate duplicate class error")
    void shouldPropagateDuplicateClassError() {
        CreateClassRequest request = validRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "DUPLICATE_CLASS",
                "A class named 'Primary 1' already exists in this session",
                "name");
        when(classService.createClass(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(classController.createClass(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(classService, times(1)).createClass(request);
    }

    @Test
    @DisplayName("Should list classes successfully")
    void shouldListClassesSuccessfully() {
        List<ClassResponse> serviceResponse = List.of(classResponse());
        when(classService.listClasses("current", "Grade 1", "ACTIVE")).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(classController.listClasses("current", "Grade 1", "ACTIVE"))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<List<ClassResponse>> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();

                    assertThat(body.getData()).hasSize(1);
                    ClassResponse data = body.getData().get(0);
                    assertThat(data.classId()).isEqualTo(CLASS_ID);
                    assertThat(data.name()).isEqualTo("Primary 1");
                    assertThat(data.currentEnrollment()).isZero();
                    assertThat(data.availableSpots()).isEqualTo(35);
                    assertThat(data.status()).isEqualTo("ACTIVE");
                })
                .verifyComplete();

        verify(classService, times(1)).listClasses("current", "Grade 1", "ACTIVE");
    }

    @Test
    @DisplayName("Should propagate list classes error")
    void shouldPropagateListClassesError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "INVALID_CLASS_FILTER",
                "Status must be ACTIVE or INACTIVE",
                "status");
        when(classService.listClasses(null, null, "ARCHIVED")).thenReturn(Mono.error(expectedError));

        StepVerifier.create(classController.listClasses(null, null, "ARCHIVED"))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(classService, times(1)).listClasses(null, null, "ARCHIVED");
    }

    @Test
    @DisplayName("Should get class details successfully")
    void shouldGetClassDetailsSuccessfully() {
        ClassDetailResponse serviceResponse = classDetailResponse();
        when(classService.getClassDetails(CLASS_ID)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(classController.getClassDetails(CLASS_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<ClassDetailResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();

                    ClassDetailResponse data = body.getData();
                    assertThat(data.classId()).isEqualTo(CLASS_ID);
                    assertThat(data.name()).isEqualTo("Primary 1");
                    assertThat(data.sessionName()).isEqualTo("2025/2026 Academic Year");
                    assertThat(data.classTeacher()).isNotNull();
                    assertThat(data.currentEnrollment()).isEqualTo(2);
                    assertThat(data.students()).hasSize(2);
                    assertThat(data.statistics().maleCount()).isEqualTo(1);
                    assertThat(data.statistics().femaleCount()).isEqualTo(1);
                })
                .verifyComplete();

        verify(classService, times(1)).getClassDetails(CLASS_ID);
    }

    @Test
    @DisplayName("Should propagate get class details error")
    void shouldPropagateGetClassDetailsError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "CLASS_NOT_FOUND",
                "Class not found or does not belong to your school");
        when(classService.getClassDetails(CLASS_ID)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(classController.getClassDetails(CLASS_ID))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(classService, times(1)).getClassDetails(CLASS_ID);
    }

    @Test
    @DisplayName("Should update class successfully")
    void shouldUpdateClassSuccessfully() {
        UpdateClassRequest request = validUpdateRequest();
        UpdateClassResponse serviceResponse = updateClassResponse();
        when(classService.updateClass(CLASS_ID, request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(classController.updateClass(CLASS_ID, request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<UpdateClassResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();

                    UpdateClassResponse data = body.getData();
                    assertThat(data.classId()).isEqualTo(CLASS_ID);
                    assertThat(data.name()).isEqualTo("Primary 1 Gold");
                    assertThat(data.classTeacher()).isEqualTo(OTHER_TEACHER_ID.toString());
                    assertThat(data.capacity()).isEqualTo(45);
                    assertThat(data.updatedAt()).isEqualTo(Instant.parse("2026-01-10T10:15:30Z"));
                })
                .verifyComplete();

        verify(classService, times(1)).updateClass(CLASS_ID, request);
    }

    @Test
    @DisplayName("Should propagate update class error")
    void shouldPropagateUpdateClassError() {
        UpdateClassRequest request = new UpdateClassRequest(null, null, null, 10);
        SchoolFeeException expectedError = new SchoolFeeException(
                "CAPACITY_TOO_LOW",
                "Cannot reduce capacity to 10. Class has 20 students enrolled.",
                "capacity");
        when(classService.updateClass(CLASS_ID, request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(classController.updateClass(CLASS_ID, request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(classService, times(1)).updateClass(CLASS_ID, request);
    }

    @Test
    @DisplayName("Should deactivate class successfully")
    void shouldDeactivateClassSuccessfully() {
        when(classService.deactivateClass(CLASS_ID)).thenReturn(Mono.empty());

        StepVerifier.create(classController.deactivateClass(CLASS_ID))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<Void> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getData()).isNull();
                    assertThat(body.getErrors()).isNull();
                })
                .verifyComplete();

        verify(classService, times(1)).deactivateClass(CLASS_ID);
    }

    @Test
    @DisplayName("Should propagate deactivate class error")
    void shouldPropagateDeactivateClassError() {
        SchoolFeeException expectedError = new SchoolFeeException(
                "CLASS_HAS_STUDENTS",
                "Cannot deactivate class with 2 students enrolled. Transfer or promote students first.",
                "classId");
        when(classService.deactivateClass(CLASS_ID)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(classController.deactivateClass(CLASS_ID))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(classService, times(1)).deactivateClass(CLASS_ID);
    }

    @Test
    @DisplayName("Should promote students successfully")
    void shouldPromoteStudentsSuccessfully() {
        PromoteStudentsRequest request = validPromotionRequest();
        PromoteStudentsResponse serviceResponse = promotionResponse();
        when(classService.promoteStudents(request)).thenReturn(Mono.just(serviceResponse));

        StepVerifier.create(classController.promoteStudents(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    ApiResponse<PromoteStudentsResponse> body = responseEntity.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.isSuccess()).isTrue();
                    assertThat(body.getTimestamp()).isNotNull();
                    assertThat(body.getErrors()).isNull();

                    PromoteStudentsResponse data = body.getData();
                    assertThat(data.promotionId()).isNotNull();
                    assertThat(data.fromClass()).isEqualTo("Primary 1");
                    assertThat(data.toClass()).isEqualTo("Primary 2");
                    assertThat(data.studentsPromoted()).isEqualTo(2);
                    assertThat(data.failedPromotions()).isEmpty();
                    assertThat(data.message()).isEqualTo("2 students promoted to Primary 2");
                })
                .verifyComplete();

        verify(classService, times(1)).promoteStudents(request);
    }

    @Test
    @DisplayName("Should propagate promote students error")
    void shouldPropagatePromoteStudentsError() {
        PromoteStudentsRequest request = validPromotionRequest();
        SchoolFeeException expectedError = new SchoolFeeException(
                "INSUFFICIENT_CAPACITY",
                "Target class only has 1 spots available. Trying to promote 2 students.",
                "studentIds");
        when(classService.promoteStudents(request)).thenReturn(Mono.error(expectedError));

        StepVerifier.create(classController.promoteStudents(request))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                .verify();

        verify(classService, times(1)).promoteStudents(request);
    }

    @Test
    @DisplayName("Should not call service before controller method is invoked")
    void shouldNotCallServiceBeforeControllerMethodIsInvoked() {
        verify(classService, never()).createClass(validRequest());
        verify(classService, never()).listClasses("current", "Grade 1", "ACTIVE");
        verify(classService, never()).getClassDetails(CLASS_ID);
        verify(classService, never()).updateClass(CLASS_ID, validUpdateRequest());
        verify(classService, never()).deactivateClass(CLASS_ID);
        verify(classService, never()).promoteStudents(validPromotionRequest());
    }

    private CreateClassRequest validRequest() {
        return new CreateClassRequest(
                "Primary 1",
                "Grade 1",
                "A",
                SESSION_ID,
                TEACHER_ID,
                35);
    }

    private ClassResponse classResponse() {
        return new ClassResponse(
                CLASS_ID,
                "Primary 1",
                "Grade 1",
                "A",
                "2025/2026 Academic Year",
                new ClassResponse.ClassTeacher(TEACHER_ID, null),
                35,
                0,
                35,
                List.of(),
                "ACTIVE",
                Instant.parse("2026-01-10T10:15:30Z"));
    }

    private ClassDetailResponse classDetailResponse() {
        return new ClassDetailResponse(
                CLASS_ID,
                "Primary 1",
                "Grade 1",
                "A",
                "2025/2026 Academic Year",
                new ClassDetailResponse.ClassTeacher(TEACHER_ID, null, null, null),
                35,
                2,
                List.of(
                        new ClassDetailResponse.StudentSummary(
                                STUDENT_ID_1,
                                "ADM-001",
                                "Ada",
                                "Lovelace",
                                "FEMALE",
                                null,
                                null),
                        new ClassDetailResponse.StudentSummary(
                                STUDENT_ID_2,
                                "ADM-002",
                                "Alan",
                                "Turing",
                                "MALE",
                                null,
                                null)),
                new ClassDetailResponse.ClassStatistics(1, 1, 0, 2),
                Instant.parse("2026-01-10T10:15:30Z"));
    }

    private UpdateClassRequest validUpdateRequest() {
        return new UpdateClassRequest(
                "Primary 1 Gold",
                "Grade 1",
                OTHER_TEACHER_ID,
                45);
    }

    private UpdateClassResponse updateClassResponse() {
        return new UpdateClassResponse(
                CLASS_ID,
                "Primary 1 Gold",
                OTHER_TEACHER_ID.toString(),
                45,
                Instant.parse("2026-01-10T10:15:30Z"));
    }

    private PromoteStudentsRequest validPromotionRequest() {
        return new PromoteStudentsRequest(
                CLASS_ID,
                TARGET_CLASS_ID,
                List.of(STUDENT_ID_1, STUDENT_ID_2),
                SESSION_ID);
    }

    private PromoteStudentsResponse promotionResponse() {
        return new PromoteStudentsResponse(
                UUID.fromString("e3333333-3333-3333-3333-333333333333"),
                "Primary 1",
                "Primary 2",
                2,
                List.of(),
                "2 students promoted to Primary 2");
    }
}
