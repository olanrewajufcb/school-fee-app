package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.domain.UserSchoolRole;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateParentRequest;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateParentResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.CreateStaffResponse;
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
    private KeycloakAdminServiceImpl keycloakAdminService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TransactionalOperator transactionalOperator;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

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
            UUID keycloakUserId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

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

            User user = User.builder()
                    .id(userId)
                    .keycloakId(keycloakUserId)
                    .schoolId(SCHOOL_ID)
                    .email("john.doe@email.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
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

            // Mock Keycloak user creation
            when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet()))
                    .thenReturn(Mono.just(keycloakUserId));

            // Mock user creation
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));

            // Mock role creation
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Mock guardian linking
            when(guardianRepository.updateUserId(any(UUID.class), any(UUID.class)))
                    .thenReturn(Mono.just(guardian));

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
                        assertThat(response.userId()).isEqualTo(userId);
                        assertThat(response.guardianId()).isEqualTo(guardianId);
                        assertThat(response.phoneNumber()).isEqualTo("+2348012345678");
                        assertThat(response.email()).isEqualTo("john.doe@email.com");
                        assertThat(response.firstName()).isEqualTo("John");
                        assertThat(response.lastName()).isEqualTo("Doe");
                        assertThat(response.userType()).isEqualTo("PARENT");
                        assertThat(response.childrenLinked()).isEqualTo(2);
                        assertThat(response.message()).contains("Parent account created");
                    })
                    .verifyComplete();

            // Verify all steps were executed
            verify(studentRepository, times(2)).findById(any(UUID.class));
            verify(guardianRepository).save(any(StudentGuardian.class));
            verify(keycloakAdminService).createUser(any(), eq("PARENT"), anySet());
            verify(userRepository).save(any(User.class));
            verify(roleRepository).save(any(UserSchoolRole.class));
            verify(guardianRepository).updateUserId(any(UUID.class), any(UUID.class));
            verify(guardianLinkRepository, times(2)).save(any(StudentGuardianLink.class));
            verify(outboxEventRepository).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("Should use existing guardian if phone number already exists")
        void shouldUseExistingGuardian() {
            UUID studentId1 = children.get(0).studentId();
            UUID studentId2 = children.get(1).studentId();
            UUID guardianId = UUID.randomUUID();
            UUID keycloakUserId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Student student1 = Student.builder().id(studentId1).schoolId(SCHOOL_ID).build();
            Student student2 = Student.builder().id(studentId2).schoolId(SCHOOL_ID).build();
            StudentGuardian existingGuardian = StudentGuardian.builder()
                    .id(guardianId)
                    .schoolId(SCHOOL_ID)
                    .phone("2348012345678")
                    .build();

            User user = User.builder()
                    .id(userId)
                    .keycloakId(keycloakUserId)
                    .schoolId(SCHOOL_ID)
                    .userType("PARENT")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(studentRepository.findById(studentId1)).thenReturn(Mono.just(student1));
            when(studentRepository.findById(studentId2)).thenReturn(Mono.just(student2));
            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull("2348012345678", SCHOOL_ID))
                    .thenReturn(Mono.just(existingGuardian));
            when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet()))
                    .thenReturn(Mono.just(keycloakUserId));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));
            when(roleRepository.save(any(UserSchoolRole.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(guardianRepository.updateUserId(any(UUID.class), any(UUID.class)))
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
            verify(keycloakAdminService, never()).createUser(any(), any(), any());
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
                    "John", "Doe", "+2348012345678", "john@email.com",
                    List.of(new CreateParentRequest.ChildLink(studentId, "FATHER", true))
            );

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));

            Mono<CreateParentResponse> result = userManagementService.createParent(singleChildRequest);

            StepVerifier.create(result)
                    .expectError(Throwable.class)
                    .verify();

            verify(keycloakAdminService, never()).createUser(any(), any(), any());
            verify(guardianRepository, never()).findByPhoneAndSchoolIdAndDeletedAtIsNull(anyString(), any());
        }

        @Test
        @DisplayName("Should handle duplicate parent role gracefully")
        void shouldHandleDuplicateParentRole() {
            UUID studentId1 = children.get(0).studentId();
            UUID studentId2 = children.get(1).studentId();
            UUID guardianId = UUID.randomUUID();
            UUID keycloakUserId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            Student student1 = Student.builder().id(studentId1).schoolId(SCHOOL_ID).build();
            Student student2 = Student.builder().id(studentId2).schoolId(SCHOOL_ID).build();
            StudentGuardian guardian = StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build();
            User user = User.builder().id(userId).keycloakId(keycloakUserId).schoolId(SCHOOL_ID).userType("PARENT").isActive(true).build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(studentRepository.findById(studentId1)).thenReturn(Mono.just(student1));
            when(studentRepository.findById(studentId2)).thenReturn(Mono.just(student2));
            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull(anyString(), eq(SCHOOL_ID)))
                    .thenReturn(Mono.empty());
            when(guardianRepository.save(any(StudentGuardian.class))).thenReturn(Mono.just(guardian));
            when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet())).thenReturn(Mono.just(keycloakUserId));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(user));
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenReturn(Mono.error(new DuplicateKeyException("duplicate role")));
            when(guardianRepository.updateUserId(any(UUID.class), any(UUID.class))).thenReturn(Mono.just(guardian));
            when(guardianLinkRepository.save(any(StudentGuardianLink.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenReturn(Mono.just(OutboxEvent.builder().id(UUID.randomUUID()).build()));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Mono<CreateParentResponse> result = userManagementService.createParent(validRequest);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.userId()).isEqualTo(userId);
                    })
                    .verifyComplete();

            // Should continue despite duplicate role error
            verify(roleRepository).save(any(UserSchoolRole.class));
        }

        @Test
        @DisplayName("Should create Keycloak cleanup event when DB transaction fails")
        void shouldCreateCleanupEventWhenTransactionFails() {
            UUID studentId = children.getFirst().studentId();
            UUID guardianId = UUID.randomUUID();
            UUID keycloakUserId = UUID.randomUUID();

            Student student = Student.builder().id(studentId).schoolId(SCHOOL_ID).build();
            StudentGuardian guardian = StudentGuardian.builder().id(guardianId).schoolId(SCHOOL_ID).build();

            // Create a single-child request to avoid validation issues with unmocked second student
            CreateParentRequest singleChildRequest = new CreateParentRequest(
                    "John", "Doe", "+2348012345678", "john@email.com",
                    List.of(new CreateParentRequest.ChildLink(studentId, "FATHER", true))
            );

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
            when(studentRepository.findById(studentId)).thenReturn(Mono.just(student));
            when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull(any(), eq(SCHOOL_ID)))
                    .thenReturn(Mono.empty());
            when(guardianRepository.save(any(StudentGuardian.class))).thenReturn(Mono.just(guardian));
            when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet())).thenReturn(Mono.just(keycloakUserId));
            when(userRepository.save(any(User.class))).thenReturn(Mono.error(new RuntimeException("DB error")));
            when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            when(outboxEventRepository.save(any(OutboxEvent.class)))
                    .thenReturn(Mono.just(OutboxEvent.builder().id(UUID.randomUUID()).build()));
            when(transactionalOperator.transactional(any(Mono.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Mono<CreateParentResponse> result = userManagementService.createParent(singleChildRequest);

            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();

            // Cleanup event should be created
            ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository, atLeastOnce()).save(eventCaptor.capture());

            OutboxEvent cleanupEvent = eventCaptor.getAllValues().stream()
                    .filter(e -> "KEYCLOAK_CLEANUP".equals(e.getEventType()))
                    .findFirst()
                    .orElse(null);

            assertThat(cleanupEvent).isNotNull();
            assertThat(cleanupEvent.getAggregateId()).isEqualTo(keycloakUserId);
        }
    }

    @Test
    @DisplayName("Should create Keycloak cleanup event when DB transaction fails")
    void shouldCreateCleanupEventWhenTransactionFails() {
        // Arrange
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        UUID keycloakUserId = UUID.randomUUID();
        UUID cleanupEventId = UUID.randomUUID();

        Student student = Student.builder().id(studentId).schoolId(SCHOOL_ID).build();
        StudentGuardian guardian = StudentGuardian.builder()
                .id(guardianId).schoolId(SCHOOL_ID).build();

        CreateParentRequest request = new CreateParentRequest(
                "John", "Doe", "+2348012345678", "john@email.com",
                List.of(new CreateParentRequest.ChildLink(studentId, "FATHER", true))
        );

        // All successful steps before the DB transaction
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
        when(studentRepository.findById(studentId)).thenReturn(Mono.just(student));
        when(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull(any(), eq(SCHOOL_ID)))
                .thenReturn(Mono.just(guardian)); // Guardian already exists
        when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet()))
                .thenReturn(Mono.just(keycloakUserId));

        // DB operations — user save FAILS
        when(userRepository.save(any(User.class)))
                .thenReturn(Mono.error(new RuntimeException("DB connection pool exhausted")));

        // Outbox save for cleanup event SUCCEEDS
        OutboxEvent savedCleanupEvent = OutboxEvent.builder()
                .id(cleanupEventId)
                .eventType("KEYCLOAK_CLEANUP")
                .aggregateId(keycloakUserId)
                .build();
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(Mono.just(savedCleanupEvent));

        // TransactionalOperator pass-through
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // ObjectMapper for JSON payload
        when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());

        // Act
        Mono<CreateParentResponse> result = userManagementService.createParent(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(error ->
                        error instanceof RuntimeException &&
                                "DB connection pool exhausted".equals(error.getMessage()))
                .verify();

        // Verify: outbox event repository was called with a KEYCLOAK_CLEANUP event
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(captor.capture());

        OutboxEvent capturedEvent = captor.getValue();
        assertThat(capturedEvent.getEventType()).isEqualTo("KEYCLOAK_CLEANUP");
        assertThat(capturedEvent.getAggregateId()).isEqualTo(keycloakUserId);
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
}
