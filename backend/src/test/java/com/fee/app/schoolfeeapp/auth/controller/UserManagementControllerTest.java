package com.fee.app.schoolfeeapp.auth.controller;

import com.fee.app.schoolfeeapp.auth.dto.request.CreateParentRequest;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateParentResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateStaffResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.UserSummaryResponse;
import com.fee.app.schoolfeeapp.auth.service.UserManagementService;
import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementControllerTest {

    @Mock
    private UserManagementService userManagementService;

    @InjectMocks
    private UserManagementController userManagementController;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID GUARDIAN_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");

    // ========================================================================
    // CREATE PARENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Parent")
    class CreateParentTests {

        private CreateParentRequest validRequest;

        @BeforeEach
        void setup() {
            validRequest = new CreateParentRequest(
                    "John",
                    "Doe",
                    "+2348012345678",
                    "john.doe@email.com",
                    List.of(
                            new CreateParentRequest.ChildLink(
                                    UUID.randomUUID(),
                                    "FATHER",
                                    true
                            )
                    )
            );
        }

        @Test
        @DisplayName("Should create parent successfully")
        void shouldCreateParentSuccessfully() {
            CreateParentResponse expectedResponse = new CreateParentResponse(
                    USER_ID,
                    GUARDIAN_ID,
                    "+2348012345678",
                    "john.doe@email.com",
                    "John",
                    "Doe",
                    "PARENT",
                    1,
                    true,
                    "Pending",
                    "Parent account created. Invitation will be sent shortly."
            );

            when(userManagementService.createParent(any(CreateParentRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            Mono<ResponseEntity<ApiResponse<CreateParentResponse>>> result =
                    userManagementController.createParent(validRequest);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().getData()).isNotNull();
                        assertThat(responseEntity.getBody().getData().userId()).isEqualTo(USER_ID);
                        assertThat(responseEntity.getBody().getData().guardianId()).isEqualTo(GUARDIAN_ID);
                        assertThat(responseEntity.getBody().getData().email()).isEqualTo("john.doe@email.com");
                        assertThat(responseEntity.getBody().getData().message())
                                .isEqualTo("Parent account created. Invitation will be sent shortly.");
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).createParent(validRequest);
        }

        @Test
        @DisplayName("Should handle validation errors")
        void shouldHandleValidationErrors() {
            CreateParentRequest invalidRequest = new CreateParentRequest(
                    "",  // Empty first name
                    "Doe",
                    "invalid-phone",
                    "invalid-email",
                    Collections.emptyList()  // No children
            );

            SchoolFeeException expectedError = new SchoolFeeException(
                    "VALIDATION_ERROR",
                    "Validation failed"
            );

            when(userManagementService.createParent(any(CreateParentRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            Mono<ResponseEntity<ApiResponse<CreateParentResponse>>> result =
                    userManagementController.createParent(invalidRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(userManagementService, times(1)).createParent(invalidRequest);
        }

        @Test
        @DisplayName("Should handle student not found error")
        void shouldHandleStudentNotFoundError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "STUDENT_NOT_FOUND",
                    "Student not found: " + UUID.randomUUID()
            );

            when(userManagementService.createParent(any(CreateParentRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            Mono<ResponseEntity<ApiResponse<CreateParentResponse>>> result =
                    userManagementController.createParent(validRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle duplicate guardian error")
        void shouldHandleDuplicateGuardianError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "DUPLICATE_GUARDIAN",
                    "Guardian with this phone number already exists"
            );

            when(userManagementService.createParent(any(CreateParentRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            Mono<ResponseEntity<ApiResponse<CreateParentResponse>>> result =
                    userManagementController.createParent(validRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }
    }

    // ========================================================================
    // CREATE STAFF TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Staff")
    class CreateStaffTests {

        private CreateStaffRequest validRequest;

        @BeforeEach
        void setup() {
            validRequest = new CreateStaffRequest(
                    "Jane",
                    "Smith",
                    "jane.smith@school.edu",
                    "+2348098765432",
                    "TEACHER",
                    Set.of("TEACHER")
            );
        }

        @Test
        @DisplayName("Should create staff successfully")
        void shouldCreateStaffSuccessfully() {
            CreateStaffResponse expectedResponse = new CreateStaffResponse(
                    USER_ID,
                    "jane.smith@school.edu",
                    "Jane",
                    "Smith",
                    "TEACHER",
                    Set.of("TEACHER"),
                    SCHOOL_ID,
                    null,
                    "Pending",
                    "Staff account created. Credentials will be sent to jane.smith@school.edu"
            );

            when(userManagementService.createStaff(any(CreateStaffRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            Mono<ResponseEntity<ApiResponse<CreateStaffResponse>>> result =
                    userManagementController.createStaff(validRequest);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().getData()).isNotNull();
                        assertThat(responseEntity.getBody().getData().userId()).isEqualTo(USER_ID);
                        assertThat(responseEntity.getBody().getData().email()).isEqualTo("jane.smith@school.edu");
                        assertThat(responseEntity.getBody().getData().userType()).isEqualTo("TEACHER");
                        assertThat(responseEntity.getBody().getData().roles()).contains("TEACHER");
                        assertThat(responseEntity.getBody().getData().message())
                                .isEqualTo("Staff account created. Credentials will be sent to jane.smith@school.edu");
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).createStaff(validRequest);
        }

        @Test
        @DisplayName("Should create school admin successfully")
        void shouldCreateSchoolAdminSuccessfully() {
            CreateStaffRequest adminRequest = new CreateStaffRequest(
                    "Admin",
                    "User",
                    "admin@school.edu",
                    "+2348011111111",
                    "SCHOOL_ADMIN",
                    Set.of("SCHOOL_ADMIN", "ACCOUNTANT")
            );

            CreateStaffResponse expectedResponse = new CreateStaffResponse(
                    USER_ID,
                    "admin@school.edu",
                    "Admin",
                    "User",
                    "SCHOOL_ADMIN",
                    Set.of("SCHOOL_ADMIN", "ACCOUNTANT"),
                    SCHOOL_ID,
                    null,
                    "Pending",
                    "Staff account created. Credentials will be sent to admin@school.edu"
            );

            when(userManagementService.createStaff(any(CreateStaffRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            Mono<ResponseEntity<ApiResponse<CreateStaffResponse>>> result =
                    userManagementController.createStaff(adminRequest);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(responseEntity.getBody().getData().userType()).isEqualTo("SCHOOL_ADMIN");
                        assertThat(responseEntity.getBody().getData().roles())
                                .containsExactlyInAnyOrder("SCHOOL_ADMIN", "ACCOUNTANT");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should create accountant successfully")
        void shouldCreateAccountantSuccessfully() {
            CreateStaffRequest accountantRequest = new CreateStaffRequest(
                    "Accountant",
                    "User",
                    "accountant@school.edu",
                    "+2348022222222",
                    "ACCOUNTANT",
                    Set.of("ACCOUNTANT")
            );

            CreateStaffResponse expectedResponse = new CreateStaffResponse(
                    USER_ID,
                    "accountant@school.edu",
                    "Accountant",
                    "User",
                    "ACCOUNTANT",
                    Set.of("ACCOUNTANT"),
                    SCHOOL_ID,
                    null,
                    "Pending",
                    "Staff account created. Credentials will be sent to accountant@school.edu"
            );

            when(userManagementService.createStaff(any(CreateStaffRequest.class)))
                    .thenReturn(Mono.just(expectedResponse));

            Mono<ResponseEntity<ApiResponse<CreateStaffResponse>>> result =
                    userManagementController.createStaff(accountantRequest);

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(responseEntity.getBody().getData().userType()).isEqualTo("ACCOUNTANT");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle invalid user type error")
        void shouldHandleInvalidUserTypeError() {
            CreateStaffRequest invalidRequest = new CreateStaffRequest(
                    "Invalid",
                    "Type",
                    "invalid@school.edu",
                    "+2348033333333",
                    "INVALID_TYPE",
                    Set.of("INVALID_ROLE")
            );

            SchoolFeeException expectedError = new SchoolFeeException(
                    "INVALID_USER_TYPE",
                    "User type must be SCHOOL_ADMIN, ACCOUNTANT, or TEACHER"
            );

            when(userManagementService.createStaff(any(CreateStaffRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            Mono<ResponseEntity<ApiResponse<CreateStaffResponse>>> result =
                    userManagementController.createStaff(invalidRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle email already in use error")
        void shouldHandleEmailAlreadyInUseError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "EMAIL_ALREADY_EXISTS",
                    "Email already in use: jane.smith@school.edu"
            );

            when(userManagementService.createStaff(any(CreateStaffRequest.class)))
                    .thenReturn(Mono.error(expectedError));

            Mono<ResponseEntity<ApiResponse<CreateStaffResponse>>> result =
                    userManagementController.createStaff(validRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }
    }

    // ========================================================================
    // LIST USERS TESTS
    // ========================================================================

    @Nested
    @DisplayName("List Users")
    class ListUsersTests {

        @Test
        @DisplayName("Should list users with default parameters")
        void shouldListUsersWithDefaultParameters() {
            UserSummaryResponse user1 = new UserSummaryResponse(
                    USER_ID,
                    "user1@school.edu",
                    "+2348012345678",
                    "John",
                    "Doe",
                    "PARENT",
                    Set.of("PARENT"),
                    true,
                    2,
                    ZonedDateTime.now().minusSeconds(86400),
                    Instant.now().minusSeconds(2592000)
            );

            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    List.of(user1),
                    0,
                    10,
                    1L,
                    1
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userId");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().getData()).isNotNull();
                        assertThat(responseEntity.getBody().getData().content()).hasSize(1);
                        assertThat(responseEntity.getBody().getData().totalElements()).isEqualTo(1L);
                        assertThat(responseEntity.getBody().getData().page()).isEqualTo(0);
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString());
        }

        @Test
        @DisplayName("Should list users with filters")
        void shouldListUsersWithFilters() {
            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    Collections.emptyList(),
                    0,
                    10,
                    0L,
                    0
            );

            when(userManagementService.listUsers(
                    eq("PARENT"), eq("ACTIVE"), eq("John"), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers("PARENT", "ACTIVE", "John", 0, 10, "email");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().content()).isEmpty();
                        assertThat(responseEntity.getBody().getData().totalElements()).isEqualTo(0L);
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).listUsers(
                    eq("PARENT"), eq("ACTIVE"), eq("John"), any(Pageable.class), anyString());
        }

        @Test
        @DisplayName("Should list inactive users")
        void shouldListInactiveUsers() {
            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    Collections.emptyList(),
                    0,
                    10,
                    0L,
                    0
            );

            when(userManagementService.listUsers(
                    isNull(), eq("INACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "INACTIVE", null, 0, 10, "userId");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).listUsers(
                    isNull(), eq("INACTIVE"), isNull(), any(Pageable.class), anyString());
        }

        @Test
        @DisplayName("Should paginate correctly")
        void shouldPaginateCorrectly() {
            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    Collections.emptyList(),
                    2,
                    20,
                    45L,
                    3
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 2, 20, "createdAt");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getBody().getData().page()).isEqualTo(2);
                        assertThat(responseEntity.getBody().getData().size()).isEqualTo(20);
                        assertThat(responseEntity.getBody().getData().totalElements()).isEqualTo(45L);
                        assertThat(responseEntity.getBody().getData().totalPages()).isEqualTo(3);
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).listUsers(
                    isNull(), eq("ACTIVE"), isNull(), argThat(pageable ->
                            pageable.getPageNumber() == 2 &&
                            pageable.getPageSize() == 20 &&
                            pageable.getSort().getOrderFor("createdAt") != null
                    ), anyString());
        }

        @Test
        @DisplayName("Should sort by different columns")
        void shouldSortByDifferentColumns() {
            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    Collections.emptyList(),
                    0,
                    10,
                    0L,
                    0
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            // Test sorting by firstName
            userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "firstName")
                    .block();

            verify(userManagementService, times(1)).listUsers(
                    isNull(), eq("ACTIVE"), isNull(), argThat(pageable ->
                            pageable.getSort().getOrderFor("firstName") != null
                    ), anyString());
        }

        @Test
        @DisplayName("Should reject invalid sort column")
        void shouldRejectInvalidSortColumn() {
            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "invalidColumn");

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(userManagementService, never()).listUsers(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should handle page number zero correctly")
        void shouldHandlePageNumberZero() {
            // Page 0 is valid (first page)
            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    Collections.emptyList(),
                    0,
                    10,
                    0L,
                    0
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userId");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().page()).isEqualTo(0);
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).listUsers(
                    isNull(), eq("ACTIVE"), isNull(), argThat(pageable ->
                            pageable.getPageNumber() == 0
                    ), anyString());
        }

        @Test
        @DisplayName("Should handle minimum page size correctly")
        void shouldHandleMinimumPageSize() {
            // Size 1 is the minimum valid page size
            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    Collections.emptyList(),
                    0,
                    1,
                    0L,
                    0
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 1, "userId");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().size()).isEqualTo(1);
                    })
                    .verifyComplete();

            verify(userManagementService, times(1)).listUsers(
                    isNull(), eq("ACTIVE"), isNull(), argThat(pageable ->
                            pageable.getPageSize() == 1
                    ), anyString());
        }

        @Test
        @DisplayName("Should handle database timeout error")
        void shouldHandleDatabaseTimeoutError() {
            SchoolFeeException expectedError = new SchoolFeeException(
                    "RESOURCE_TIMEOUT",
                    "DB timed out"
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.error(expectedError));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userId");

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle empty result set")
        void shouldHandleEmptyResultSet() {
            PageResponse<UserSummaryResponse> emptyPage = new PageResponse<>(
                    Collections.emptyList(),
                    0,
                    10,
                    0L,
                    0
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(emptyPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userId");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().content()).isEmpty();
                        assertThat(responseEntity.getBody().getData().totalElements()).isEqualTo(0L);
                        assertThat(responseEntity.getBody().getData().totalPages()).isEqualTo(0);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle multiple users in response")
        void shouldHandleMultipleUsersInResponse() {
            UserSummaryResponse user1 = new UserSummaryResponse(
                    UUID.randomUUID(),
                    "user1@school.edu",
                    "+2348012345678",
                    "John",
                    "Doe",
                    "PARENT",
                    Set.of("PARENT"),
                    true,
                    2,
                    ZonedDateTime.now().minusSeconds(86400),
                    Instant.now().minusSeconds(2592000)
            );

            UserSummaryResponse user2 = new UserSummaryResponse(
                    UUID.randomUUID(),
                    "user2@school.edu",
                    "+2348098765432",
                    "Jane",
                    "Smith",
                    "TEACHER",
                    Set.of("TEACHER"),
                    true,
                    0,
                    ZonedDateTime.now().minusSeconds(7200),
                    Instant.now().minusSeconds(1209600)
            );

            PageResponse<UserSummaryResponse> expectedPage = new PageResponse<>(
                    List.of(user1, user2),
                    0,
                    10,
                    2L,
                    1
            );

            when(userManagementService.listUsers(
                    isNull(), eq("ACTIVE"), isNull(), any(Pageable.class), anyString()))
                    .thenReturn(Mono.just(expectedPage));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userId");

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getBody().getData().content()).hasSize(2);
                        assertThat(responseEntity.getBody().getData().content().get(0).userType())
                                .isEqualTo("PARENT");
                        assertThat(responseEntity.getBody().getData().content().get(1).userType())
                                .isEqualTo("TEACHER");
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // SORT COLUMN VALIDATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Sort Column Validation")
    class SortColumnValidationTests {

        @Test
        @DisplayName("Should accept userId as sort column")
        void shouldAcceptUserIdSort() {
            when(userManagementService.listUsers(any(), any(), any(), any(), anyString()))
                    .thenReturn(Mono.just(new PageResponse<>(Collections.emptyList(), 0, 10, 0L, 0)));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userId");

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should accept email as sort column")
        void shouldAcceptEmailSort() {
            when(userManagementService.listUsers(any(), any(), any(), any(), anyString()))
                    .thenReturn(Mono.just(new PageResponse<>(Collections.emptyList(), 0, 10, 0L, 0)));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "email");

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should accept firstName as sort column")
        void shouldAcceptFirstNameSort() {
            when(userManagementService.listUsers(any(), any(), any(), any(), anyString()))
                    .thenReturn(Mono.just(new PageResponse<>(Collections.emptyList(), 0, 10, 0L, 0)));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "firstName");

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should accept lastName as sort column")
        void shouldAcceptLastNameSort() {
            when(userManagementService.listUsers(any(), any(), any(), any(), anyString()))
                    .thenReturn(Mono.just(new PageResponse<>(Collections.emptyList(), 0, 10, 0L, 0)));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "lastName");

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should accept userType as sort column")
        void shouldAcceptUserTypeSort() {
            when(userManagementService.listUsers(any(), any(), any(), any(), anyString()))
                    .thenReturn(Mono.just(new PageResponse<>(Collections.emptyList(), 0, 10, 0L, 0)));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userType");

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should accept lastLogin as sort column")
        void shouldAcceptLastLoginSort() {
            when(userManagementService.listUsers(any(), any(), any(), any(), anyString()))
                    .thenReturn(Mono.just(new PageResponse<>(Collections.emptyList(), 0, 10, 0L, 0)));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "lastLogin");

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should accept createdAt as sort column")
        void shouldAcceptCreatedAtSort() {
            when(userManagementService.listUsers(any(), any(), any(), any(), anyString()))
                    .thenReturn(Mono.just(new PageResponse<>(Collections.emptyList(), 0, 10, 0L, 0)));

            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "createdAt");

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should reject SQL injection attempt in sort column")
        void shouldRejectSqlInjectionAttempt() {
            Mono<ResponseEntity<ApiResponse<PageResponse<UserSummaryResponse>>>> result =
                    userManagementController.listUsers(null, "ACTIVE", null, 0, 10, "userId; DROP TABLE users");

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }
    }
}
