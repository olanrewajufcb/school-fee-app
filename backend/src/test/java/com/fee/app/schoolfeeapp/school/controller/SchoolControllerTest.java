package com.fee.app.schoolfeeapp.school.controller;

import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.dto.request.CloseSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.CreateAcademicSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.CreateSchoolRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSessionRequest;
import com.fee.app.schoolfeeapp.school.dto.request.UpdateSchoolRequest;
import com.fee.app.schoolfeeapp.school.dto.response.AcademicSessionResponse;
import com.fee.app.schoolfeeapp.school.dto.response.CloseSessionResponse;
import com.fee.app.schoolfeeapp.school.dto.response.CreateSchoolResponse;
import com.fee.app.schoolfeeapp.school.dto.response.SchoolResponse;
import com.fee.app.schoolfeeapp.school.dto.response.SchoolSummaryResponse;
import com.fee.app.schoolfeeapp.school.dto.response.SetCurrentTermResponse;
import com.fee.app.schoolfeeapp.school.dto.response.UpdateSessionResponse;
import com.fee.app.schoolfeeapp.school.service.SchoolService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolControllerTest {

    @Mock
    private SchoolService schoolService;

    @InjectMocks
    private SchoolController schoolController;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID SESSION_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID TERM_ID = UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901234");

    @Nested
    @DisplayName("Create School")
    class CreateSchoolTests {

        @Test
        @DisplayName("Should create school successfully")
        void shouldCreateSchoolSuccessfully() {
            CreateSchoolRequest request = validCreateSchoolRequest();
            CreateSchoolResponse serviceResponse = createSchoolResponse(true);

            when(schoolService.createSchool(request)).thenReturn(Mono.just(serviceResponse));

            Mono<ResponseEntity<ApiResponse<CreateSchoolResponse>>> result =
                    schoolController.createSchool(request);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getTimestamp()).isNotNull();
                        assertThat(responseEntity.getBody().getErrors()).isNull();

                        CreateSchoolResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.schoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(data.name()).isEqualTo("Grace International School");
                        assertThat(data.code()).isEqualTo("GIS");
                        assertThat(data.status()).isEqualTo("ACTIVE");
                        assertThat(data.adminUserCreated()).isTrue();
                        assertThat(data.currentSessionId()).isEqualTo(SESSION_ID);
                        assertThat(data.message()).contains("Admin credentials sent");
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).createSchool(request);
        }

        @Test
        @DisplayName("Should propagate duplicate school code error")
        void shouldPropagateDuplicateSchoolCodeError() {
            CreateSchoolRequest request = validCreateSchoolRequest();
            SchoolFeeException expectedError = new SchoolFeeException(
                    "DUPLICATE_RESOURCE",
                    "School code 'GIS' is already in use",
                    "code");

            when(schoolService.createSchool(request)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.createSchool(request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isSameAs(expectedError);
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                    })
                    .verify();

            verify(schoolService, times(1)).createSchool(request);
        }

        @Test
        @DisplayName("Should propagate invalid term config error")
        void shouldPropagateInvalidTermConfigError() {
            CreateSchoolRequest request = validCreateSchoolRequest();
            SchoolFeeException expectedError = new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Academic year start must use MM-dd format",
                    "termConfig.academicYearStart");

            when(schoolService.createSchool(request)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.createSchool(request))
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(schoolService, times(1)).createSchool(request);
        }
    }

    @Nested
    @DisplayName("Get Current School")
    class GetCurrentSchoolTests {

        @Test
        @DisplayName("Should return current school successfully")
        void shouldReturnCurrentSchoolSuccessfully() {
            SchoolResponse serviceResponse = schoolResponse(true);
            when(schoolService.getCurrentSchool()).thenReturn(Mono.just(serviceResponse));

            Mono<ResponseEntity<ApiResponse<SchoolResponse>>> result =
                    schoolController.getCurrentSchool();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getTimestamp()).isNotNull();

                        SchoolResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.schoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(data.currentTerm()).isNotNull();
                        assertThat(data.currentTerm().termId()).isEqualTo(TERM_ID);
                        assertThat(data.paymentConfig()).containsEntry("paystackPublicKey", "123456");
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).getCurrentSchool();
        }

        @Test
        @DisplayName("Should propagate current school not found error")
        void shouldPropagateCurrentSchoolNotFoundError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SCHOOL_NOT_FOUND",
                    "School not found");

            when(schoolService.getCurrentSchool()).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.getCurrentSchool())
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).getCurrentSchool();
        }
    }

    @Nested
    @DisplayName("Get School By Id")
    class GetSchoolByIdTests {

        @Test
        @DisplayName("Should return school by id successfully")
        void shouldReturnSchoolByIdSuccessfully() {
            SchoolResponse serviceResponse = schoolResponse(true);
            when(schoolService.getSchoolById(SCHOOL_ID)).thenReturn(Mono.just(serviceResponse));

            Mono<ResponseEntity<ApiResponse<SchoolResponse>>> result =
                    schoolController.getSchool(SCHOOL_ID);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getData()).isEqualTo(serviceResponse);
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).getSchoolById(SCHOOL_ID);
        }

        @Test
        @DisplayName("Should propagate school by id not found error")
        void shouldPropagateSchoolByIdNotFoundError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SCHOOL_NOT_FOUND",
                    "School not found: " + SCHOOL_ID);

            when(schoolService.getSchoolById(SCHOOL_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.getSchool(SCHOOL_ID))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).getSchoolById(SCHOOL_ID);
        }
    }

    @Nested
    @DisplayName("Update School")
    class UpdateSchoolTests {

        @Test
        @DisplayName("Should update school successfully")
        void shouldUpdateSchoolSuccessfully() {
            UpdateSchoolRequest request = validUpdateSchoolRequest();
            SchoolResponse serviceResponse = schoolResponse(false);

            when(schoolService.updateSchool(request)).thenReturn(Mono.just(serviceResponse));

            Mono<ResponseEntity<ApiResponse<SchoolResponse>>> result =
                    schoolController.updateSchool(request);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getData()).isEqualTo(serviceResponse);
                        assertThat(responseEntity.getBody().getData().email()).isEqualTo("updated@gis.edu");
                        assertThat(responseEntity.getBody().getData().currentTerm()).isNull();
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).updateSchool(request);
        }

        @Test
        @DisplayName("Should propagate stale school update error")
        void shouldPropagateStaleSchoolUpdateError() {
            UpdateSchoolRequest request = validUpdateSchoolRequest();
            SchoolFeeException expectedError = new SchoolFeeException(
                    "STALE_RESOURCE",
                    "School was modified by another request. Please reload and try again.",
                    "version");

            when(schoolService.updateSchool(request)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.updateSchool(request))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).updateSchool(request);
        }

        @Test
        @DisplayName("Should propagate update school not found error")
        void shouldPropagateUpdateSchoolNotFoundError() {
            UpdateSchoolRequest request = validUpdateSchoolRequest();
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SCHOOL_NOT_FOUND",
                    "School not found");

            when(schoolService.updateSchool(request)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.updateSchool(request))
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(schoolService, times(1)).updateSchool(request);
        }
    }

    @Nested
    @DisplayName("Deactivate School")
    class DeactivateSchoolTests {

        @Test
        @DisplayName("Should deactivate school successfully")
        void shouldDeactivateSchoolSuccessfully() {
            when(schoolService.deactivateSchool(SCHOOL_ID)).thenReturn(Mono.empty());

            Mono<ResponseEntity<ApiResponse<Void>>> result =
                    schoolController.deactivateSchool(SCHOOL_ID);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getData()).isNull();
                        assertThat(responseEntity.getBody().getErrors()).isNull();
                        assertThat(responseEntity.getBody().getTimestamp()).isNotNull();
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).deactivateSchool(SCHOOL_ID);
        }

        @Test
        @DisplayName("Should propagate deactivate school not found error")
        void shouldPropagateDeactivateSchoolNotFoundError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SCHOOL_NOT_FOUND",
                    "School not found: " + SCHOOL_ID);

            when(schoolService.deactivateSchool(SCHOOL_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.deactivateSchool(SCHOOL_ID))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).deactivateSchool(SCHOOL_ID);
        }

        @Test
        @DisplayName("Should propagate stale deactivation error")
        void shouldPropagateStaleDeactivationError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "STALE_RESOURCE",
                    "School was modified by another request. Please reload and try again.",
                    "version");

            when(schoolService.deactivateSchool(SCHOOL_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.deactivateSchool(SCHOOL_ID))
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(schoolService, times(1)).deactivateSchool(SCHOOL_ID);
        }
    }

    @Nested
    @DisplayName("List Schools")
    class ListSchoolsTests {

        @Test
        @DisplayName("Should list schools successfully with default pagination")
        void shouldListSchoolsSuccessfullyWithDefaultPagination() {
            PageResponse<SchoolSummaryResponse> serviceResponse = schoolSummaryPage();
            when(schoolService.listSchools(eq("ACTIVE"), argThat(pageable ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 20)))
                    .thenReturn(Mono.just(serviceResponse));

            Mono<ResponseEntity<ApiResponse<PageResponse<SchoolSummaryResponse>>>> result =
                    schoolController.listSchools("ACTIVE", 0, 20);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getData()).isEqualTo(serviceResponse);
                        assertThat(responseEntity.getBody().getData().content()).hasSize(2);
                        assertThat(responseEntity.getBody().getData().totalElements()).isEqualTo(2);
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).listSchools(eq("ACTIVE"), argThat(pageable ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 20));
        }

        @Test
        @DisplayName("Should pass custom list school filters to service")
        void shouldPassCustomListSchoolFiltersToService() {
            PageResponse<SchoolSummaryResponse> serviceResponse = new PageResponse<>(
                    List.of(schoolSummary("Inactive School", "INS", "INACTIVE")),
                    2,
                    5,
                    11,
                    3);
            when(schoolService.listSchools(eq("INACTIVE"), argThat(pageable ->
                    pageable.getPageNumber() == 2 && pageable.getPageSize() == 5)))
                    .thenReturn(Mono.just(serviceResponse));

            StepVerifier.create(schoolController.listSchools("INACTIVE", 2, 5))
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().getData().page()).isEqualTo(2);
                        assertThat(responseEntity.getBody().getData().size()).isEqualTo(5);
                        assertThat(responseEntity.getBody().getData().totalPages()).isEqualTo(3);
                        assertThat(responseEntity.getBody().getData().content().getFirst().status())
                                .isEqualTo("INACTIVE");
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).listSchools(eq("INACTIVE"), argThat(pageable ->
                    pageable.getPageNumber() == 2 && pageable.getPageSize() == 5));
        }

        @Test
        @DisplayName("Should propagate list schools error")
        void shouldPropagateListSchoolsError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "INVALID_STATUS",
                    "School status must be one of ACTIVE, INACTIVE, or ALL",
                    "status");
            when(schoolService.listSchools(eq("ARCHIVED"), argThat(pageable ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 20)))
                    .thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.listSchools("ARCHIVED", 0, 20))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).listSchools(eq("ARCHIVED"), argThat(pageable ->
                    pageable.getPageNumber() == 0 && pageable.getPageSize() == 20));
        }
    }

    @Nested
    @DisplayName("Get Sessions")
    class GetSessionsTests {

        @Test
        @DisplayName("Should return sessions successfully")
        void shouldReturnSessionsSuccessfully() {
            List<AcademicSessionResponse> serviceResponse = List.of(academicSessionResponse());
            when(schoolService.getCurrentSchoolSessions()).thenReturn(Mono.just(serviceResponse));

            Mono<ResponseEntity<ApiResponse<List<AcademicSessionResponse>>>> result =
                    schoolController.getSessions();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getData()).hasSize(1);
                        assertThat(responseEntity.getBody().getData().getFirst().sessionId()).isEqualTo(SESSION_ID);
                        assertThat(responseEntity.getBody().getData().getFirst().terms()).hasSize(1);
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).getCurrentSchoolSessions();
        }

        @Test
        @DisplayName("Should return empty sessions successfully")
        void shouldReturnEmptySessionsSuccessfully() {
            when(schoolService.getCurrentSchoolSessions()).thenReturn(Mono.just(List.of()));

            StepVerifier.create(schoolController.getSessions())
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().getData()).isEmpty();
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).getCurrentSchoolSessions();
        }

        @Test
        @DisplayName("Should propagate get sessions error")
        void shouldPropagateGetSessionsError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SCHOOL_NOT_FOUND",
                    "School not found");
            when(schoolService.getCurrentSchoolSessions()).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.getSessions())
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).getCurrentSchoolSessions();
        }
    }

    @Nested
    @DisplayName("Create Session")
    class CreateSessionTests {

        @Test
        @DisplayName("Should create session successfully")
        void shouldCreateSessionSuccessfully() {
            CreateAcademicSessionRequest request = validCreateAcademicSessionRequest();
            AcademicSessionResponse serviceResponse = academicSessionResponse();
            when(schoolService.createSession(request)).thenReturn(Mono.just(serviceResponse));

            StepVerifier.create(schoolController.createSession(request))
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getTimestamp()).isNotNull();

                        AcademicSessionResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.sessionId()).isEqualTo(SESSION_ID);
                        assertThat(data.name()).isEqualTo("2025/2026 Academic Year");
                        assertThat(data.isCurrent()).isTrue();
                        assertThat(data.terms()).hasSize(1);
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).createSession(request);
        }

        @Test
        @DisplayName("Should propagate create session error")
        void shouldPropagateCreateSessionError() {
            CreateAcademicSessionRequest request = validCreateAcademicSessionRequest();
            SchoolFeeException expectedError = new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Terms must not overlap",
                    "terms");
            when(schoolService.createSession(request)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.createSession(request))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).createSession(request);
        }
    }

    @Nested
    @DisplayName("Set Current Session")
    class SetCurrentSessionTests {

        @Test
        @DisplayName("Should set current session successfully")
        void shouldSetCurrentSessionSuccessfully() {
            AcademicSessionResponse serviceResponse = academicSessionResponse();
            when(schoolService.setCurrentSession(SESSION_ID)).thenReturn(Mono.just(serviceResponse));

            StepVerifier.create(schoolController.setCurrentSession(SESSION_ID))
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getTimestamp()).isNotNull();

                        AcademicSessionResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.sessionId()).isEqualTo(SESSION_ID);
                        assertThat(data.isCurrent()).isTrue();
                        assertThat(data.terms()).hasSize(1);
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).setCurrentSession(SESSION_ID);
        }

        @Test
        @DisplayName("Should propagate set current session error")
        void shouldPropagateSetCurrentSessionError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SESSION_NOT_FOUND",
                    "Session not found: " + SESSION_ID);
            when(schoolService.setCurrentSession(SESSION_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.setCurrentSession(SESSION_ID))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).setCurrentSession(SESSION_ID);
        }
    }

    @Nested
    @DisplayName("Update Session")
    class UpdateSessionTests {

        @Test
        @DisplayName("Should update session successfully")
        void shouldUpdateSessionSuccessfully() {
            UpdateSessionRequest request = validUpdateSessionRequest();
            UpdateSessionResponse serviceResponse = updateSessionResponse();
            when(schoolService.updateSession(SESSION_ID, request)).thenReturn(Mono.just(serviceResponse));

            StepVerifier.create(schoolController.updateSession(SESSION_ID, request))
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getTimestamp()).isNotNull();

                        UpdateSessionResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.sessionId()).isEqualTo(SESSION_ID);
                        assertThat(data.name()).isEqualTo("2025/2026 Revised Academic Year");
                        assertThat(data.updatedAt()).isNotNull();
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).updateSession(SESSION_ID, request);
        }

        @Test
        @DisplayName("Should propagate update session error")
        void shouldPropagateUpdateSessionError() {
            UpdateSessionRequest request = validUpdateSessionRequest();
            SchoolFeeException expectedError = new SchoolFeeException(
                    "INVALID_TERM_CONFIG",
                    "Terms must not overlap",
                    "terms");
            when(schoolService.updateSession(SESSION_ID, request)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.updateSession(SESSION_ID, request))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).updateSession(SESSION_ID, request);
        }
    }

    @Nested
    @DisplayName("Close Session")
    class CloseSessionTests {

        @Test
        @DisplayName("Should close session successfully")
        void shouldCloseSessionSuccessfully() {
            CloseSessionRequest request = validCloseSessionRequest();
            CloseSessionResponse serviceResponse = closeSessionResponse();
            when(schoolService.closeSession(SESSION_ID, request)).thenReturn(Mono.just(serviceResponse));

            StepVerifier.create(schoolController.closeSession(SESSION_ID, request))
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getTimestamp()).isNotNull();

                        CloseSessionResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.sessionId()).isEqualTo(SESSION_ID);
                        assertThat(data.status()).isEqualTo("COMPLETED");
                        assertThat(data.closedAt()).isEqualTo(Instant.parse("2026-01-10T10:15:30Z"));
                        assertThat(data.termsCompleted()).isEqualTo(3);
                        assertThat(data.studentsArchived()).isZero();
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).closeSession(SESSION_ID, request);
        }

        @Test
        @DisplayName("Should propagate close session error")
        void shouldPropagateCloseSessionError() {
            CloseSessionRequest request = validCloseSessionRequest();
            SchoolFeeException expectedError = new SchoolFeeException(
                    "CANNOT_CLOSE_CURRENT_SESSION",
                    "Please set another session as current before closing this one.",
                    "sessionId");
            when(schoolService.closeSession(SESSION_ID, request)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.closeSession(SESSION_ID, request))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).closeSession(SESSION_ID, request);
        }
    }

    @Test
    @DisplayName("Should not call service before controller method is invoked")
    void shouldNotCallServiceBeforeControllerMethodIsInvoked() {
        verify(schoolService, never()).createSchool(validCreateSchoolRequest());
        verify(schoolService, never()).getCurrentSchool();
        verify(schoolService, never()).getSchoolById(SCHOOL_ID);
        verify(schoolService, never()).updateSchool(validUpdateSchoolRequest());
        verify(schoolService, never()).deactivateSchool(SCHOOL_ID);
        verify(schoolService, never()).listSchools(eq("ACTIVE"), org.mockito.ArgumentMatchers.<Pageable>any());
        verify(schoolService, never()).getCurrentSchoolSessions();
        verify(schoolService, never()).createSession(validCreateAcademicSessionRequest());
        verify(schoolService, never()).setCurrentSession(SESSION_ID);
        verify(schoolService, never()).updateSession(SESSION_ID, validUpdateSessionRequest());
        verify(schoolService, never()).closeSession(SESSION_ID, validCloseSessionRequest());
    }

    private PageResponse<SchoolSummaryResponse> schoolSummaryPage() {
        return new PageResponse<>(
                List.of(
                        schoolSummary("Grace International School", "GIS", "ACTIVE"),
                        schoolSummary("Bright Academy", "BAS", "ACTIVE")),
                0,
                20,
                2,
                1);
    }

    private SchoolSummaryResponse schoolSummary(String name, String code, String status) {
        return new SchoolSummaryResponse(
                SCHOOL_ID,
                name,
                code,
                "Lagos",
                "Lagos",
                0,
                0,
                status,
                "First Term 2025/2026 Academic Year",
                0.0,
                Instant.parse("2026-01-10T10:15:30Z"));
    }

    private AcademicSessionResponse academicSessionResponse() {
        return new AcademicSessionResponse(
                SESSION_ID,
                "2025/2026 Academic Year",
                LocalDate.of(2025, 9, 8),
                LocalDate.of(2026, 9, 7),
                true,
                List.of(new AcademicSessionResponse.TermResponse(
                        TERM_ID,
                        "First Term",
                        1,
                        LocalDate.of(2025, 9, 8),
                        LocalDate.of(2025, 12, 19),
                        true)));
    }

    private CreateAcademicSessionRequest validCreateAcademicSessionRequest() {
        return new CreateAcademicSessionRequest(
                "2026/2027 Academic Year",
                LocalDate.of(2026, 9, 8),
                LocalDate.of(2027, 9, 7),
                List.of(
                        new CreateAcademicSessionRequest.TermRequest(
                                "First Term",
                                1,
                                LocalDate.of(2026, 9, 8),
                                LocalDate.of(2026, 12, 19)),
                        new CreateAcademicSessionRequest.TermRequest(
                                "Second Term",
                                2,
                                LocalDate.of(2027, 1, 5),
                        LocalDate.of(2027, 4, 4))),
                true);
    }

    private UpdateSessionRequest validUpdateSessionRequest() {
        return new UpdateSessionRequest(
                "2025/2026 Revised Academic Year",
                LocalDate.of(2025, 9, 1),
                LocalDate.of(2026, 9, 7),
                List.of(new UpdateSessionRequest.TermUpdate(
                        TERM_ID,
                        "Opening Term",
                        LocalDate.of(2025, 9, 1),
                        LocalDate.of(2025, 12, 18))));
    }

    private UpdateSessionResponse updateSessionResponse() {
        return new UpdateSessionResponse(
                SESSION_ID,
                "2025/2026 Revised Academic Year",
                Instant.parse("2026-01-10T10:15:30Z"));
    }

    private CloseSessionRequest validCloseSessionRequest() {
        return new CloseSessionRequest(true, false, "End of session");
    }

    private CloseSessionResponse closeSessionResponse() {
        return new CloseSessionResponse(
                SESSION_ID,
                "2025/2026 Academic Year",
                "COMPLETED",
                Instant.parse("2026-01-10T10:15:30Z"),
                3,
                0,
                "Session closed. Terms marked completed.");
    }

    private CreateSchoolRequest validCreateSchoolRequest() {
        return new CreateSchoolRequest(
                "Grace International School",
                "GIS",
                "hello@gis.edu",
                "+2348012345678",
                "12 School Road",
                "Lagos",
                "Lagos",
                "Nigeria",
                "https://cdn.example.com/logo.png",
                new CreateSchoolRequest.PaymentConfig(
                        "123456",
                        "GIS",
                        List.of("PAYSTACK", "CARD"),
                        "FLWPUBK_TEST",
                        "FLWSECK_TEST"),
                new CreateSchoolRequest.SmsConfig(
                        "AFRICASTALKING",
                        "secret-key",
                        "gis-user",
                        "GIS",
                        "234"),
                new CreateSchoolRequest.TermConfig(
                        3,
                        List.of("First Term", "Second Term", "Third Term"),
                        "09-08"),
                new CreateSchoolRequest.AdminUser(
                        "admin@gis.edu",
                        "Ada",
                        "Lovelace",
                        "+2348098765432"));
    }

    private UpdateSchoolRequest validUpdateSchoolRequest() {
        return new UpdateSchoolRequest(
                "updated@gis.edu",
                "+2348011111111",
                "34 Updated Avenue",
                "Ikeja",
                "Lagos",
                "https://cdn.example.com/new-logo.png",
                new UpdateSchoolRequest.PaymentConfig(
                        "654321",
                        "NEWGIS",
                        List.of("BANK_TRANSFER", "CARD")),
                new UpdateSchoolRequest.SmsConfig(
                        "AFRICASTALKING",
                        "secret-key",
                        "gis-user",
                        "GIS"));
    }

    private CreateSchoolResponse createSchoolResponse(boolean adminCreated) {
        return CreateSchoolResponse.builder()
                .schoolId(SCHOOL_ID)
                .name("Grace International School")
                .code("GIS")
                .status("ACTIVE")
                .adminUserCreated(adminCreated)
                .adminTemporaryPassword(adminCreated ? "Sent via email" : "Manual setup required")
                .currentSessionId(SESSION_ID)
                .currentSessionName("2025/2026 Academic Year")
                .createdAt(Instant.parse("2026-01-10T10:15:30Z"))
                .message(adminCreated
                        ? "School created successfully. Admin credentials sent to admin@gis.edu"
                        : "School created but admin account setup failed. Please contact support.")
                .build();
    }

    private SchoolResponse schoolResponse(boolean includeCurrentTerm) {
        return SchoolResponse.builder()
                .schoolId(SCHOOL_ID)
                .name("Grace International School")
                .code("GIS")
                .email(includeCurrentTerm ? "hello@gis.edu" : "updated@gis.edu")
                .phone(includeCurrentTerm ? "+2348012345678" : "+2348011111111")
                .address(includeCurrentTerm ? "12 School Road" : "34 Updated Avenue")
                .city(includeCurrentTerm ? "Lagos" : "Ikeja")
                .state("Lagos")
                .country("Nigeria")
                .logoUrl(includeCurrentTerm
                        ? "https://cdn.example.com/logo.png"
                        : "https://cdn.example.com/new-logo.png")
                .status("ACTIVE")
                .currentTerm(includeCurrentTerm ? currentTermResponse() : null)
                .paymentConfig(Map.of(
                        "paystackPublicKey", includeCurrentTerm ? "123456" : "654321",
                        "paystackSubaccountCode", includeCurrentTerm ? "GIS" : "NEWGIS",
                        "acceptedPaymentMethods", List.of("PAYSTACK", "CARD")))
                .createdAt(Instant.parse("2026-01-10T10:15:30Z"))
                .build();
    }

    private SchoolResponse.CurrentTerm currentTermResponse() {
        return SchoolResponse.CurrentTerm.builder()
                .termId(TERM_ID)
                .name("First Term")
                .sessionName("2025/2026 Academic Year")
                .startDate("2025-09-08")
                .endDate("2025-12-19")
                .build();
    }

    @Nested
    @DisplayName("Set Current Term")
    class SetCurrentTermTests {

        @Test
        @DisplayName("Should set term as current successfully")
        void shouldSetTermAsCurrentSuccessfully() {
            SetCurrentTermResponse serviceResponse = new SetCurrentTermResponse(
                    TERM_ID,
                    "Second Term",
                    "2025/2026 Academic Year",
                    true,
                    LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 4, 4),
                    new SetCurrentTermResponse.PreviousTerm(
                            UUID.fromString("e5f6a7b8-9012-3456-ef12-345678901235"),
                            "First Term",
                            "COMPLETED"));

            when(schoolService.setCurrentTerm(TERM_ID)).thenReturn(Mono.just(serviceResponse));

            Mono<ResponseEntity<ApiResponse<SetCurrentTermResponse>>> result =
                    schoolController.setCurrentTerm(TERM_ID);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();

                        SetCurrentTermResponse data = responseEntity.getBody().getData();
                        assertThat(data).isNotNull();
                        assertThat(data.termId()).isEqualTo(TERM_ID);
                        assertThat(data.name()).isEqualTo("Second Term");
                        assertThat(data.sessionName()).isEqualTo("2025/2026 Academic Year");
                        assertThat(data.isCurrent()).isTrue();
                        assertThat(data.previousCurrentTerm()).isNotNull();
                        assertThat(data.previousCurrentTerm().name()).isEqualTo("First Term");
                        assertThat(data.previousCurrentTerm().status()).isEqualTo("COMPLETED");
                    })
                    .verifyComplete();

            verify(schoolService, times(1)).setCurrentTerm(TERM_ID);
        }

        @Test
        @DisplayName("Should propagate term not found error")
        void shouldPropagateTermNotFoundError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "TERM_NOT_FOUND",
                    "Term not found: " + TERM_ID);

            when(schoolService.setCurrentTerm(TERM_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.setCurrentTerm(TERM_ID))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).setCurrentTerm(TERM_ID);
        }

        @Test
        @DisplayName("Should propagate session closed error")
        void shouldPropagateSessionClosedError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "SESSION_ALREADY_CLOSED",
                    "Session is already closed and cannot be modified: " + SESSION_ID);

            when(schoolService.setCurrentTerm(TERM_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.setCurrentTerm(TERM_ID))
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(schoolService, times(1)).setCurrentTerm(TERM_ID);
        }

        @Test
        @DisplayName("Should propagate term not in school error")
        void shouldPropagateTermNotInSchoolError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "TERM_NOT_IN_SCHOOL",
                    "Term does not belong to your school");

            when(schoolService.setCurrentTerm(TERM_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.setCurrentTerm(TERM_ID))
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(schoolService, times(1)).setCurrentTerm(TERM_ID);
        }

        @Test
        @DisplayName("Should propagate term not in current session error")
        void shouldPropagateTermNotInCurrentSessionError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "TERM_NOT_IN_CURRENT_SESSION",
                    "Term does not belong to the current academic session",
                    "termId");

            when(schoolService.setCurrentTerm(TERM_ID)).thenReturn(Mono.error(expectedError));

            StepVerifier.create(schoolController.setCurrentTerm(TERM_ID))
                    .expectErrorSatisfies(error -> assertThat(error).isSameAs(expectedError))
                    .verify();

            verify(schoolService, times(1)).setCurrentTerm(TERM_ID);
        }
    }
}
