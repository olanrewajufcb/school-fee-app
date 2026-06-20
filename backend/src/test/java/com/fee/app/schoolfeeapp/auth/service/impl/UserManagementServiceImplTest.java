package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLinkProjection;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.domain.UserSchoolRole;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateParentRequest;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateParentResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateStaffResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;
import com.fee.app.schoolfeeapp.auth.dto.response.UserSummaryResponse;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserSchoolRoleRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.dto.PageResponse;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.notification.service.SmsService;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSchoolRoleRepository roleRepository;

    @Mock
    private StudentGuardianRepository guardianRepository;

    @Mock
    private StudentGuardianLinkRepository guardianLinkRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private KeycloakAdminServiceImpl keycloakAdminService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TransactionalOperator transactionalOperator;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private UserManagementServiceImpl userManagementService;

    private static final UUID ADMIN_USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final String SCHOOL_NAME = "Grace International School";

    private SchoolFeeUser adminUser;

    @BeforeEach
    void setup() {
        adminUser = SchoolFeeUser.builder()
                .userId(ADMIN_USER_ID)
                .email("admin@school.edu")
                .phoneNumber("+2348012345678")
                .firstName("Admin")
                .lastName("User")
                .userType("SCHOOL_ADMIN")
                .schoolId(SCHOOL_ID)
                .schoolName(SCHOOL_NAME)
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }

    // ========================================================================
    // CREATE PARENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Parent")
    class CreateParentTests {

        private CreateParentRequest validRequest;
        private List<CreateParentRequest.ChildLink> children;

        @BeforeEach
        void setup() {
            UUID studentId1 = UUID.randomUUID();
            UUID studentId2 = UUID.randomUUID();

            children = List.of(
                    new CreateParentRequest.ChildLink(studentId1, "FATHER", true),
                    new CreateParentRequest.ChildLink(studentId2, "MOTHER", false)
            );

            validRequest = new CreateParentRequest(
                    "+2348012345678",
                    "john.doe@email.com",
                    "John",
                    "Doe",
                    children
            );
        }

        @Test
        @DisplayName("Should create parent successfully with all steps")
        void shouldCreateParentSuccessfully() {
            UUID studentId1 = children.get(0).studentId();
            UUID studentId2 = children.get(1).studentId();
            UUID guardianId = UUID.randomUUID();

            Student student1 = Student.builder().id(studentId1).schoolId(SCHOOL_ID).build();
            Student student2 = Student.builder().id(studentId2).schoolId(SCHOOL_ID).build();

            StudentGuardian guardian = StudentGuardian.builder()
                    .id(guardianId)
                    .schoolId(SCHOOL_ID)
                    .firstName("John")
                    .lastName("Doe")
                    .phone("2348012345678")
                    .email("john.doe@email.com")
                    .build();

            // Mock JWT
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));

            // Mock student validation
            when(studentRepository.findById(studentId1)).thenReturn(Mono.just(student1));
            when(studentRepository.findById(studentId2)).thenReturn(Mono.just(student2));

            // Mock guardian creation
            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull(any(), eq(SCHOOL_ID)))
                    .thenReturn(Mono.empty());
            when(guardianRepository.save(any(StudentGuardian.class))).thenReturn(Mono.just(guardian));

            // Mock guardian-student links
            when(guardianLinkRepository.save(any(StudentGuardianLink.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Mock outbox event
            when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenReturn(Mono.just(OutboxEvent.builder().id(UUID.randomUUID()).build()));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Mono<CreateParentResponse> result = userManagementService.createParent(validRequest);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.userId()).isNull();
                        assertThat(response.guardianId()).isEqualTo(guardianId);
                        assertThat(response.phoneNumber()).isEqualTo("+2348012345678");
                        assertThat(response.email()).isEqualTo("john.doe@email.com");
                        assertThat(response.firstName()).isEqualTo("John");
                        assertThat(response.lastName()).isEqualTo("Doe");
                        assertThat(response.userType()).isEqualTo("PARENT");
                        assertThat(response.childrenLinked()).isEqualTo(2);
                        assertThat(response.message()).contains("Guardian added");
                    })
                    .verifyComplete();

            // Verify all steps were executed
            verify(studentRepository, times(2)).findById(any(UUID.class));
            verify(guardianRepository).save(any(StudentGuardian.class));
            verify(guardianLinkRepository, times(2)).save(any(StudentGuardianLink.class));
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should use existing guardian if phone number already exists")
        void shouldUseExistingGuardian() {
            UUID studentId1 = children.get(0).studentId();
            UUID studentId2 = children.get(1).studentId();
            UUID guardianId = UUID.randomUUID();

            Student student1 = Student.builder().id(studentId1).schoolId(SCHOOL_ID).build();
            Student student2 = Student.builder().id(studentId2).schoolId(SCHOOL_ID).build();
            StudentGuardian existingGuardian = StudentGuardian.builder()
                    .id(guardianId)
                    .schoolId(SCHOOL_ID)
                    .phone("2348012345678")
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(studentRepository.findById(studentId1)).thenReturn(Mono.just(student1));
            when(studentRepository.findById(studentId2)).thenReturn(Mono.just(student2));
            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull("2348012345678", SCHOOL_ID))
                    .thenReturn(Mono.just(existingGuardian));
            when(guardianLinkRepository.save(any(StudentGuardianLink.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenReturn(Mono.just(OutboxEvent.builder().id(UUID.randomUUID()).build()));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Mono<CreateParentResponse> result = userManagementService.createParent(validRequest);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.guardianId()).isEqualTo(guardianId);
                    })
                    .verifyComplete();

            // Guardian should NOT be created (already exists)
            verify(guardianRepository, never()).save(any(StudentGuardian.class));
            verify(guardianRepository).findByPhoneAndSchoolIdAndDeletedAtIsNull(anyString(), eq(SCHOOL_ID));
        }

        @Test
        @DisplayName("Should fail if student not found")
        void shouldFailIfStudentNotFound() {
            UUID invalidStudentId = UUID.randomUUID();
            CreateParentRequest invalidRequest = new CreateParentRequest(
                    "John", "Doe", "+2348012345678", "john@email.com",
                    List.of(new CreateParentRequest.ChildLink(invalidStudentId, "FATHER", true))
            );

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(studentRepository.findById(invalidStudentId)).thenReturn(Mono.empty());

            Mono<CreateParentResponse> result = userManagementService.createParent(invalidRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(studentRepository).findById(invalidStudentId);
            verify(guardianRepository, never()).findByPhoneAndSchoolIdAndDeletedAtIsNull(any(), any());
        }

        @Test
        @DisplayName("Should fail if student belongs to different school")
        void shouldFailIfStudentBelongsToDifferentSchool() {
            UUID studentId = children.getFirst().studentId();
            UUID otherSchoolId = UUID.randomUUID();

            Student student = Student.builder().id(studentId).schoolId(otherSchoolId).build();

            // Create a request with only this one student to avoid issues with unmocked second student
            CreateParentRequest singleChildRequest = new CreateParentRequest(
                    "+2348012345678", "john@email.com", "John", "Doe",
                    List.of(new CreateParentRequest.ChildLink(studentId, "FATHER", true))
            );

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(studentRepository.findById(studentId)).thenReturn(Mono.just(student));

            Mono<CreateParentResponse> result = userManagementService.createParent(singleChildRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(guardianRepository, never()).findByPhoneAndSchoolIdAndDeletedAtIsNull(anyString(), any());
        }
    }

    

    // ========================================================================
    // CREATE STAFF TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Staff")
    class CreateStaffTests {

        private CreateStaffRequest teacherRequest;
        private CreateStaffRequest adminRequest;

        @BeforeEach
        void setup() {
            teacherRequest = new CreateStaffRequest(
                    "Jane",
                    "Smith",
                    "jane.smith@school.edu",
                    "+2348098765432",
                    "TEACHER",
                    Set.of("TEACHER")
            );

            adminRequest = new CreateStaffRequest(
                    "Admin",
                    "User",
                    "admin2@school.edu",
                    "+2348011111111",
                    "SCHOOL_ADMIN",
                    Set.of("SCHOOL_ADMIN", "ACCOUNTANT")
            );

            lenient().when(userRepository.findByKeycloakIdAndDeletedAtIsNull(any()))
                    .thenReturn(Mono.just(User.builder().id(ADMIN_USER_ID).build()));
        }

        @Test
        @DisplayName("Should create teacher successfully")
        void shouldCreateTeacherSuccessfully() {
            UUID userId = UUID.randomUUID();

            User user = User.builder()
                    .id(userId)
                    .schoolId(SCHOOL_ID)
                    .email("jane.smith@school.edu")
                    .phone("2348098765432")
                    .firstName("Jane")
                    .lastName("Smith")
                    .userType("TEACHER")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));
            when(roleRepository.save(any(UserSchoolRole.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenReturn(Mono.just(OutboxEvent.builder().id(UUID.randomUUID()).build()));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Mono<CreateStaffResponse> result = userManagementService.createStaff(teacherRequest);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.userId()).isEqualTo(userId);
                        assertThat(response.email()).isEqualTo("jane.smith@school.edu");
                        assertThat(response.userType()).isEqualTo("TEACHER");
                        assertThat(response.roles()).contains("TEACHER");
                        assertThat(response.message()).contains("Credentials will be sent");
                    })
                    .verifyComplete();

            verify(userRepository).save(any(User.class));
            verify(roleRepository).save(any(UserSchoolRole.class));
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should create school admin with multiple roles")
        void shouldCreateSchoolAdminWithMultipleRoles() {
            UUID userId = UUID.randomUUID();

            User user = User.builder()
                    .id(userId)
                    .schoolId(SCHOOL_ID)
                    .email("admin2@school.edu")
                    .userType("SCHOOL_ADMIN")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));
            when(roleRepository.save(any(UserSchoolRole.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenReturn(Mono.just(OutboxEvent.builder().id(UUID.randomUUID()).build()));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Mono<CreateStaffResponse> result = userManagementService.createStaff(adminRequest);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.userType()).isEqualTo("SCHOOL_ADMIN");
                        assertThat(response.roles()).containsExactlyInAnyOrder("SCHOOL_ADMIN", "ACCOUNTANT");
                    })
                    .verifyComplete();

            // Should create 2 roles
            verify(roleRepository, times(2)).save(any(UserSchoolRole.class));
        }

        @Test
        @DisplayName("Should fail for invalid user type")
        void shouldFailForInvalidUserType() {
            CreateStaffRequest invalidRequest = new CreateStaffRequest(
                    "Invalid", "Type", "invalid@school.edu", "+2348022222222",
                    "INVALID_TYPE", Set.of("INVALID_ROLE")
            );

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));

            Mono<CreateStaffResponse> result = userManagementService.createStaff(invalidRequest);

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should create outbox event for async Keycloak processing")
        void shouldCreateOutboxEventForKeycloakProcessing() {
            UUID userId = UUID.randomUUID();
            User user = User.builder().id(userId).schoolId(SCHOOL_ID).userType("TEACHER").isActive(true).build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));
            when(roleRepository.save(any(UserSchoolRole.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenReturn(Mono.just(OutboxEvent.builder().id(UUID.randomUUID()).build()));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Mono<CreateStaffResponse> result = userManagementService.createStaff(teacherRequest);

            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();

            // Verify outbox event was created
            ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(eventCaptor.capture());
            
            OutboxEvent savedEvent = eventCaptor.getValue();
            assertThat(savedEvent.getEventType()).isEqualTo("STAFF_CREATED");
            assertThat(savedEvent.getAggregateId()).isEqualTo(userId);
            assertThat(savedEvent.getStatus()).isEqualTo("PENDING");
        }
    }

    // ========================================================================
    // LIST USERS TESTS
    // ========================================================================

    @Nested
    @DisplayName("List Users")
    class ListUsersTests {

        @Test
        @DisplayName("Should list users with filters")
        void shouldListUsersWithFilters() {
            UUID userId = UUID.randomUUID();
            User user = User.builder()
                    .id(userId)
                    .schoolId(SCHOOL_ID)
                    .email("test@school.edu")
                    .phone("2348012345678")
                    .firstName("Test")
                    .lastName("User")
                    .userType("PARENT")
                    .isActive(true)
                    .createdAt(Instant.now())
                    .lastLogin(ZonedDateTime.now())
                    .build();

            Pageable pageable = PageRequest.of(0, 10);

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(userRepository.findBySchoolIdWithFilters(eq(SCHOOL_ID), eq("PARENT"), eq(true), isNull(), eq(10), eq(0L)))
                    .thenReturn(Flux.just(user));
            when(userRepository.countBySchoolIdWithFilters(eq(SCHOOL_ID), eq("PARENT"), eq(true), isNull()))
                    .thenReturn(Mono.just(1L));
            when(guardianRepository.findByUserIdAndDeletedAtIsNull(eq(userId))).thenReturn(Mono.empty());
            when(roleRepository.findByUserIdAndIsActiveTrue(eq(userId))).thenReturn(Flux.empty());

            Mono<PageResponse<UserSummaryResponse>> result = userManagementService.listUsers(
                    "PARENT", "ACTIVE", null, pageable, "request-id"
            );

            StepVerifier.create(result)
                    .assertNext(pageResponse -> {
                        assertThat(pageResponse.content()).hasSize(1);
                        assertThat(pageResponse.totalElements()).isEqualTo(1L);
                        assertThat(pageResponse.page()).isEqualTo(0);
                        assertThat(pageResponse.size()).isEqualTo(10);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return empty page when no users found")
        void shouldReturnEmptyPageWhenNoUsersFound() {
            Pageable pageable = PageRequest.of(0, 10);

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(userRepository.findBySchoolIdWithFilters(any(), any(), any(), any(), anyInt(), anyLong()))
                    .thenReturn(Flux.empty());
            when(userRepository.countBySchoolIdWithFilters(any(), any(), any(), any()))
                    .thenReturn(Mono.just(0L));

            Mono<PageResponse<UserSummaryResponse>> result = userManagementService.listUsers(
                    null, "ACTIVE", null, pageable, "request-id"
            );

            StepVerifier.create(result)
                    .assertNext(pageResponse -> {
                        assertThat(pageResponse.content()).isEmpty();
                        assertThat(pageResponse.totalElements()).isEqualTo(0L);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle database timeout")
        void shouldHandleDatabaseTimeout() {
            Pageable pageable = PageRequest.of(0, 10);

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(userRepository.findBySchoolIdWithFilters(any(), any(), any(), any(), anyInt(), anyLong()))
                    .thenReturn(Flux.error(new TimeoutException("Query timed out")));

            Mono<PageResponse<UserSummaryResponse>> result = userManagementService.listUsers(
                    null, "ACTIVE", null, pageable, "request-id"
            );

            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should calculate total pages correctly")
        void shouldCalculateTotalPagesCorrectly() {
            Pageable pageable = PageRequest.of(0, 10);

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(userRepository.findBySchoolIdWithFilters(any(), any(), any(), any(), anyInt(), anyLong()))
                    .thenReturn(Flux.empty());
            when(userRepository.countBySchoolIdWithFilters(any(), any(), any(), any()))
                    .thenReturn(Mono.just(25L));

            Mono<PageResponse<UserSummaryResponse>> result = userManagementService.listUsers(
                    null, "ACTIVE", null, pageable, "request-id"
            );

            StepVerifier.create(result)
                    .assertNext(pageResponse -> {
                        assertThat(pageResponse.totalPages()).isEqualTo(3); // ceil(25/10) = 3
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // CHECK ACCOUNT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Check Account")
    class CheckAccountTests {

        @Test
        @DisplayName("Should return 400 Bad Request (SchoolFeeException) for invalid phone number")
        void shouldReturnBadRequestForInvalidPhoneNumber() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.CheckAccountRequest("invalid_phone");

            Mono<com.fee.app.schoolfeeapp.auth.dto.response.CheckAccountResponse> result = userManagementService.checkAccount(request);

            StepVerifier.create(result)
                    .expectErrorMatches(throwable -> throwable instanceof SchoolFeeException && 
                            ((SchoolFeeException) throwable).getErrorCode().equals("INVALID_PHONE_NUMBER"))
                    .verify();
        }

        @Test
        @DisplayName("Should deduplicate guardians in the same school and return single match")
        void shouldDeduplicateGuardiansInSameSchool() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.CheckAccountRequest("+2348012345678");

            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();

            StudentGuardian g1 = StudentGuardian.builder().id(guardianId1).schoolId(SCHOOL_ID).firstName("John").lastName("Doe").build();
            StudentGuardian g2 = StudentGuardian.builder().id(guardianId2).schoolId(SCHOOL_ID).firstName("John").lastName("Doe").build();

            School school = School.builder().id(SCHOOL_ID).name(SCHOOL_NAME).build();

            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678"))
                    .thenReturn(Flux.just(g1, g2));
            when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(school));
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(any()))
                    .thenReturn(Flux.just(mock(StudentGuardianLinkProjection.class), mock(StudentGuardianLinkProjection.class)));

            Mono<com.fee.app.schoolfeeapp.auth.dto.response.CheckAccountResponse> result = userManagementService.checkAccount(request);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.found()).isTrue();
                        assertThat(response.schoolName()).isEqualTo(SCHOOL_NAME);
                        assertThat(response.guardianName()).isEqualTo("John Doe");
                        assertThat(response.childrenCount()).isEqualTo(2);
                        assertThat(response.options()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return multiple options with school names when in multiple schools")
        void shouldReturnMultipleOptionsWithSchoolNames() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.CheckAccountRequest("+2348012345678");

            UUID schoolId2 = UUID.randomUUID();

            StudentGuardian g1 = StudentGuardian.builder().id(UUID.randomUUID()).schoolId(SCHOOL_ID).firstName("John").lastName("Doe").build();
            StudentGuardian g2 = StudentGuardian.builder().id(UUID.randomUUID()).schoolId(schoolId2).firstName("John").lastName("Doe").build();

            School school1 = School.builder().id(SCHOOL_ID).name(SCHOOL_NAME).build();
            School school2 = School.builder().id(schoolId2).name("Second School").build();

            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678"))
                    .thenReturn(Flux.just(g1, g2));
            when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(school1));
            when(schoolRepository.findById(schoolId2)).thenReturn(Mono.just(school2));

            Mono<com.fee.app.schoolfeeapp.auth.dto.response.CheckAccountResponse> result = userManagementService.checkAccount(request);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.found()).isTrue();
                        assertThat(response.options()).hasSize(2);
                        assertThat(response.options()).extracting("schoolName")
                                .containsExactlyInAnyOrder(SCHOOL_NAME, "Second School");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return zero children count when no links exist")
        void shouldReturnZeroChildrenCount() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.CheckAccountRequest("+2348012345678");

            StudentGuardian g1 = StudentGuardian.builder().id(UUID.randomUUID()).schoolId(SCHOOL_ID).firstName("John").lastName("Doe").build();
            School school = School.builder().id(SCHOOL_ID).name(SCHOOL_NAME).build();

            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678"))
                    .thenReturn(Flux.just(g1));
            when(schoolRepository.findById(SCHOOL_ID)).thenReturn(Mono.just(school));
            when(guardianLinkRepository.findByGuardianIdAndDeletedAtIsNull(g1.getId()))
                    .thenReturn(Flux.empty());

            Mono<com.fee.app.schoolfeeapp.auth.dto.response.CheckAccountResponse> result = userManagementService.checkAccount(request);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.found()).isTrue();
                        assertThat(response.childrenCount()).isZero();
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // SEND OTP TESTS
    // ========================================================================

    @Nested
    @DisplayName("Send OTP")
    class SendOtpTests {

        @Test
        @DisplayName("Should successfully send OTP when an unregistered guardian exists")
        void shouldSendOtpForValidUnregisteredGuardian() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest("+2348012345678");

            StudentGuardian guardian = StudentGuardian.builder().id(UUID.randomUUID()).schoolId(SCHOOL_ID).userId(null).build();
            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678")).thenReturn(Flux.just(guardian));
            when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());

            Mono<Void> result = userManagementService.sendOtp(request);

            StepVerifier.create(result).verifyComplete();

            verify(smsService).send(eq("2348012345678"), contains("verification code is:"));
        }

        @Test
        @DisplayName("Should return 400 when phone number is invalid")
        void shouldReturn400ForInvalidPhone() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest("invalid");

            Mono<Void> result = userManagementService.sendOtp(request);

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof SchoolFeeException && ((SchoolFeeException) e).getErrorCode().equals("INVALID_PHONE_NUMBER"))
                    .verify();
        }

        @Test
        @DisplayName("Should return 400 when guardian is not found")
        void shouldReturn400WhenGuardianNotFound() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest("+2348012345678");

            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678")).thenReturn(Flux.empty());

            Mono<Void> result = userManagementService.sendOtp(request);

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof SchoolFeeException && ((SchoolFeeException) e).getErrorCode().equals("GUARDIAN_NOT_FOUND"))
                    .verify();
        }

        @Test
        @DisplayName("Should return 400 when account is already registered")
        void shouldReturn400WhenAccountAlreadyRegistered() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest("+2348012345678");

            StudentGuardian guardian = StudentGuardian.builder().id(UUID.randomUUID()).schoolId(SCHOOL_ID).userId(UUID.randomUUID()).build();
            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678")).thenReturn(Flux.just(guardian));

            Mono<Void> result = userManagementService.sendOtp(request);

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof SchoolFeeException && ((SchoolFeeException) e).getErrorCode().equals("ACCOUNT_ALREADY_REGISTERED"))
                    .verify();
        }
    }

    // ========================================================================
    // VERIFY OTP TESTS
    // ========================================================================

    @Nested
    @DisplayName("Verify OTP")
    class VerifyOtpTests {

        @BeforeEach
        void setup() {
            lenient().when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
        }

        @Test
        @DisplayName("Should create local user and Keycloak user if new")
        void shouldCreateAccountIfNew() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.response.VerifyOtpRequest("+2348012345678", "000000", SCHOOL_ID);

            StudentGuardian guardian = StudentGuardian.builder()
                    .id(UUID.randomUUID())
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .firstName("John")
                    .lastName("Doe")
                    .phone("2348012345678")
                    .email("john@test.com")
                    .build();

            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull("2348012345678", SCHOOL_ID))
                    .thenReturn(Mono.just(guardian));
            
            when(keycloakAdminService.findByUsername("2348012345678")).thenReturn(Optional.empty());
            
            UUID kcId = UUID.randomUUID();
            when(keycloakAdminService.createUser(any(), anyString(), any())).thenReturn(Mono.just(new KeycloakUserResult(kcId, "tempPassword")));

            User savedUser = User.builder().id(UUID.randomUUID()).build();
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(savedUser));
            when(guardianRepository.save(any(StudentGuardian.class))).thenReturn(Mono.just(guardian));
            when(roleRepository.save(any(UserSchoolRole.class))).thenReturn(Mono.just(UserSchoolRole.builder().id(UUID.randomUUID()).build()));

            Mono<java.util.Map<String, String>> result = userManagementService.verifyOtpAndCreateAccount(request);

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res).containsEntry("message", "Account created. Set your password to continue.");
                        assertThat(res).containsEntry("phoneNumber", "2348012345678");
                    })
                    .verifyComplete();
            
            verify(keycloakAdminService).createUser(any(), eq("PARENT"), eq(Set.of("PARENT")));
        }

        @Test
        @DisplayName("Should link to existing Keycloak user if present")
        void shouldLinkExistingKeycloakUser() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.response.VerifyOtpRequest("+2348012345678", "000000", SCHOOL_ID);

            StudentGuardian guardian = StudentGuardian.builder()
                    .id(UUID.randomUUID())
                    .schoolId(SCHOOL_ID)
                    .userId(null)
                    .firstName("John")
                    .lastName("Doe")
                    .phone("2348012345678")
                    .email("john@test.com")
                    .build();

            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull("2348012345678", SCHOOL_ID))
                    .thenReturn(Mono.just(guardian));
            
            org.keycloak.representations.idm.UserRepresentation kcUser = new org.keycloak.representations.idm.UserRepresentation();
            kcUser.setId(UUID.randomUUID().toString());
            kcUser.setAttributes(new java.util.HashMap<>());
            
            when(keycloakAdminService.findByUsername("2348012345678")).thenReturn(Optional.of(kcUser));

            User savedUser = User.builder().id(UUID.randomUUID()).build();
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(savedUser));
            when(guardianRepository.save(any(StudentGuardian.class))).thenReturn(Mono.just(guardian));
            when(roleRepository.save(any(UserSchoolRole.class))).thenReturn(Mono.just(UserSchoolRole.builder().id(UUID.randomUUID()).build()));

            Mono<java.util.Map<String, String>> result = userManagementService.verifyOtpAndCreateAccount(request);

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res).containsEntry("message", "Account created. Set your password to continue.");
                    })
                    .verifyComplete();
            
            verify(keycloakAdminService).updateUserAttributes(eq(kcUser.getId()), any());
            verify(keycloakAdminService, never()).createUser(any(), anyString(), any());
        }

        @Test
        @DisplayName("Should return 400 when ACCOUNT_ALREADY_REGISTERED")
        void shouldReturn400IfAlreadyRegistered() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.response.VerifyOtpRequest("+2348012345678", "000000", SCHOOL_ID);

            StudentGuardian guardian = StudentGuardian.builder()
                    .id(UUID.randomUUID())
                    .schoolId(SCHOOL_ID)
                    .userId(UUID.randomUUID())
                    .build();

            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull("2348012345678", SCHOOL_ID))
                    .thenReturn(Mono.just(guardian));

            Mono<java.util.Map<String, String>> result = userManagementService.verifyOtpAndCreateAccount(request);

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof SchoolFeeException && ((SchoolFeeException) e).getErrorCode().equals("ACCOUNT_ALREADY_REGISTERED"))
                    .verify();
        }

        @Test
        @DisplayName("Should return 400 MULTIPLE_ACCOUNTS_FOUND if schoolId is missing and multiple guardians exist")
        void shouldReturn400IfMultipleAccountsFound() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.response.VerifyOtpRequest("+2348012345678", "000000", null);

            StudentGuardian g1 = StudentGuardian.builder().id(UUID.randomUUID()).build();
            StudentGuardian g2 = StudentGuardian.builder().id(UUID.randomUUID()).build();
            
            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678"))
                    .thenReturn(Flux.just(g1, g2));

            Mono<java.util.Map<String, String>> result = userManagementService.verifyOtpAndCreateAccount(request);

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof SchoolFeeException && ((SchoolFeeException) e).getErrorCode().equals("MULTIPLE_ACCOUNTS_FOUND"))
                    .verify();
        }
    }

    // ========================================================================
    // SET PASSWORD TESTS
    // ========================================================================

    @Nested
    @DisplayName("Set Password")
    class SetPasswordTests {

        @Test
        @DisplayName("Should successfully set password")
        void shouldSetPassword() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SetPasswordRequest("+2348012345678", "NewPassword123!");

            UUID userId = UUID.randomUUID();
            UUID kcId = UUID.randomUUID();
            
            StudentGuardian guardian = StudentGuardian.builder().userId(userId).build();
            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678"))
                    .thenReturn(Flux.just(guardian));

            User user = User.builder().id(userId).keycloakId(kcId).build();
            when(userRepository.findById(userId)).thenReturn(Mono.just(user));
            
            doNothing().when(keycloakAdminService).setUserPassword(anyString(), anyString(), eq(false));

            Mono<java.util.Map<String, String>> result = userManagementService.setPassword(request);

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res).containsEntry("message", "Password set. You can now log in.");
                        assertThat(res).containsEntry("phoneNumber", "2348012345678");
                    })
                    .verifyComplete();
            
            verify(keycloakAdminService).setUserPassword(kcId.toString(), "NewPassword123!", false);
        }

        @Test
        @DisplayName("Should return 400 GUARDIAN_NOT_FOUND when phone is not recognized")
        void shouldReturn400IfGuardianNotFound() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SetPasswordRequest("+2348012345678", "NewPassword123!");

            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678"))
                    .thenReturn(Flux.empty());

            Mono<java.util.Map<String, String>> result = userManagementService.setPassword(request);

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof SchoolFeeException && ((SchoolFeeException) e).getErrorCode().equals("GUARDIAN_NOT_FOUND"))
                    .verify();
        }

        @Test
        @DisplayName("Should return 400 ACCOUNT_NOT_READY when guardian exists but has no user ID")
        void shouldReturn400IfAccountNotReady() {
            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SetPasswordRequest("+2348012345678", "NewPassword123!");

            StudentGuardian guardian = StudentGuardian.builder().userId(null).build();
            when(guardianRepository.findAllByPhoneAndDeletedAtIsNull("2348012345678"))
                    .thenReturn(Flux.just(guardian));

            Mono<java.util.Map<String, String>> result = userManagementService.setPassword(request);

            StepVerifier.create(result)
                    .expectErrorMatches(e -> e instanceof SchoolFeeException && ((SchoolFeeException) e).getErrorCode().equals("ACCOUNT_NOT_READY"))
                    .verify();
        }
    }
}
