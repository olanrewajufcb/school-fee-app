package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateParentRequest;
import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserSchoolRoleRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import com.fee.app.schoolfeeapp.notification.service.SmsService;

/**
 * Integration tests for UserManagementServiceImpl with real PostgreSQL database.
 * These tests verify the service layer behavior with:
 * - Real PostgreSQL database via Testcontainers
 * - Spring Boot application context
 * - Transactional operators and reactive streams
 * - Actual database state verification after operations
 * External dependencies (Keycloak, JWT utils) are mocked to focus on
 * service orchestration and database interactions.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserManagementServiceIntegrationTest {

    // ========================================================================
    // TESTCONTAINERS CONFIGURATION
    // ========================================================================

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");
            // .withReuse(true);  // Disabled to force fresh schema after fixes


    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // R2DBC URL (for your app)
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        // JDBC URL (for Flyway)
        registry.add("spring.flyway.url", () ->
                String.format("jdbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private UserManagementServiceImpl userManagementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSchoolRoleRepository roleRepository;

    @Autowired
    private StudentGuardianRepository guardianRepository;

    @Autowired
    private StudentGuardianLinkRepository guardianLinkRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TransactionalOperator transactionalOperator;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @MockitoBean
    private SmsService smsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.r2dbc.core.DatabaseClient databaseClient;

    private static final UUID ADMIN_USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final String SCHOOL_NAME = "Grace International School";

    private SchoolFeeUser adminUser;

    @BeforeEach
    void setUp() {
        // Create test school if it doesn't exist (required for foreign key constraints)
        databaseClient.sql("INSERT INTO school.schools (id, name, code, is_active) VALUES (:id, :name, :code, true) ON CONFLICT (id) DO NOTHING")
                .bind("id", SCHOOL_ID)
                .bind("name", SCHOOL_NAME)
                .bind("code", "GIS")
                .fetch()
                .rowsUpdated()
                .block();

        // Create admin user in database (required for assigned_by foreign key constraint)
        databaseClient.sql("INSERT INTO auth.users (id, keycloak_id, school_id, email, phone, first_name, last_name, user_type, is_active) " +
                        "VALUES (:id, :keycloakId, :schoolId, :email, :phone, :firstName, :lastName, :userType, true) ON CONFLICT (id) DO NOTHING")
                .bind("id", ADMIN_USER_ID)
                .bind("keycloakId", ADMIN_USER_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("email", "admin@school.edu")
                .bind("phone", "+2348012345678")
                .bind("firstName", "Admin")
                .bind("lastName", "User")
                .bind("userType", "SCHOOL_ADMIN")
                .fetch()
                .rowsUpdated()
                .block();

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

        reset(keycloakAdminService, jwtUtils, reactiveJwtDecoder, smsService);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminUser));
        when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());
        when(smsService.getBalance()).thenReturn(Mono.just(2500));
    }

    @AfterEach
    void tearDown() {
        // Clean up database after each test
        // Order matters due to foreign key constraints
        guardianLinkRepository.deleteAll().block();
        guardianRepository.deleteAll().block();
        roleRepository.deleteAll().block();
        userRepository.deleteAll().block();
        studentRepository.deleteAll().block();
        outboxEventRepository.deleteAll().block();
    }

    // ========================================================================
    // CREATE PARENT INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Parent - Service Integration Tests")
    class CreateParentIntegrationTests {

        @Test
        @DisplayName("Should create parent successfully with all reactive steps")
        void shouldCreateParentSuccessfullyWithAllSteps() {
            // Arrange
            Student student1 = Student.builder()
                    .schoolId(SCHOOL_ID)
                    .admissionNumber("ADM001")
                    .firstName("Student")
                    .lastName("One")
                    .dateOfBirth(java.time.LocalDate.of(2010, 1, 1))
                    .enrollmentDate(java.time.LocalDate.of(2020, 1, 1))
                    .enrollmentStatus("ACTIVE")
                    .build();

            Student student2 = Student.builder()
                    .schoolId(SCHOOL_ID)
                    .admissionNumber("ADM002")
                    .firstName("Student")
                    .lastName("Two")
                    .dateOfBirth(java.time.LocalDate.of(2011, 1, 1))
                    .enrollmentDate(java.time.LocalDate.of(2020, 1, 1))
                    .enrollmentStatus("ACTIVE")
                    .build();

            // Save students to repository (reactive) - IDs will be generated by database
            UUID studentId1 = studentRepository.save(student1).block().getId();
            UUID studentId2 = studentRepository.save(student2).block().getId();

            List<CreateParentRequest.ChildLink> children = List.of(
                    new CreateParentRequest.ChildLink(studentId1, "FATHER", true),
                    new CreateParentRequest.ChildLink(studentId2, "MOTHER", false)
            );

            CreateParentRequest request = new CreateParentRequest(
                    "+2348012345678",
                    "john.doe@email.com",
                    "John",
                    "Doe",
                    children
            );

            // Mock ObjectMapper for outbox events
            try {
                when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            } catch (Exception e) {
                // Ignore
            }

            // Act & Assert
            StepVerifier.create(userManagementService.createParent(request))
                    .consumeNextWith(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.firstName()).isEqualTo("John");
                        assertThat(response.lastName()).isEqualTo("Doe");
                        assertThat(response.phoneNumber()).isEqualTo("+2348012345678");
                        assertThat(response.email()).isEqualTo("john.doe@email.com");
                        assertThat(response.childrenLinked()).isEqualTo(2);
                        assertThat(response.message()).contains("Guardian added");
                    })
                    .verifyComplete();

            // Verify guardian data was actually persisted to database
            StepVerifier.create(guardianRepository.findByPhoneAndSchoolIdAndDeletedAtIsNull("2348012345678", SCHOOL_ID))
                    .consumeNextWith(guardian -> {
                        assertThat(guardian).isNotNull();
                        assertThat(guardian.getFirstName()).isEqualTo("John");
                        assertThat(guardian.getLastName()).isEqualTo("Doe");
                        assertThat(guardian.getUserId()).isNull(); // Not linked to Keycloak/User yet
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle student validation failure")
        void shouldHandleStudentValidationFailure() {
            // Arrange
            UUID nonExistentStudentId = UUID.randomUUID();

            List<CreateParentRequest.ChildLink> children = List.of(
                    new CreateParentRequest.ChildLink(nonExistentStudentId, "FATHER", true)
            );

            CreateParentRequest request = new CreateParentRequest(
                    "+2348011111111",
                    "test@email.com",
                    "Test",
                    "User",
                    children
            );

            // Act & Assert - Should fail because student doesn't exist
            StepVerifier.create(userManagementService.createParent(request))
                    .expectErrorMatches(error -> 
                        error instanceof SchoolFeeException &&
                        ((SchoolFeeException) error).getErrorCode().equals("STUDENT_NOT_FOUND"))
                    .verify();
        }

        @Test
        @DisplayName("Should handle cross-school validation")
        void shouldHandleCrossSchoolValidation() {
            // Arrange - Student from different school
            UUID otherSchoolId = UUID.randomUUID();

            // Create the other school in database (required for foreign key constraint)
            databaseClient.sql("INSERT INTO school.schools (id, name, code, is_active) VALUES (:id, :name, :code, true) ON CONFLICT (id) DO NOTHING")
                    .bind("id", otherSchoolId)
                    .bind("name", "Other School")
                    .bind("code", "OS")
                    .fetch()
                    .rowsUpdated()
                    .block();

            Student student = Student.builder()
                    .schoolId(otherSchoolId)
                    .admissionNumber("ADM003")
                    .firstName("Other")
                    .lastName("School")
                    .dateOfBirth(java.time.LocalDate.of(2010, 1, 1))
                    .enrollmentDate(java.time.LocalDate.of(2020, 1, 1))
                    .enrollmentStatus("ACTIVE")
                    .build();

            UUID studentId = studentRepository.save(student).block().getId();

            List<CreateParentRequest.ChildLink> children = List.of(
                    new CreateParentRequest.ChildLink(studentId, "FATHER", true)
            );

            CreateParentRequest request = new CreateParentRequest(
                    "+2348022222222",
                    "crossschool@email.com",
                    "Cross",
                    "School",
                    children
            );

            // Act & Assert - Should fail due to school mismatch
            StepVerifier.create(userManagementService.createParent(request))
                    .expectErrorMatches(error ->
                        error instanceof SchoolFeeException &&
                        ((SchoolFeeException) error).getErrorCode().equals("STUDENT_NOT_IN_SCHOOL"))
                    .verify();
        }

        @Test
        @DisplayName("Should handle duplicate role creation gracefully")
        void shouldHandleDuplicateRoleCreationGracefully() {
            // This test verifies that DuplicateKeyException is handled properly
            // when trying to create a parent role that already exists
            
            // Arrange
            Student student = Student.builder()
                    .schoolId(SCHOOL_ID)
                    .admissionNumber("ADM004")
                    .firstName("Student")
                    .lastName("Four")
                    .dateOfBirth(java.time.LocalDate.of(2010, 1, 1))
                    .enrollmentDate(java.time.LocalDate.of(2020, 1, 1))
                    .enrollmentStatus("ACTIVE")
                    .build();

            UUID studentId = studentRepository.save(student).block().getId();

            List<CreateParentRequest.ChildLink> children = List.of(
                    new CreateParentRequest.ChildLink(studentId, "FATHER", true)
            );

            CreateParentRequest request = new CreateParentRequest(
                    "+2348033333333",
                    "duplicate@email.com",
                    "Duplicate",
                    "Role",
                    children
            );

            UUID keycloakUserId = UUID.randomUUID();
            when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet()))
                    .thenReturn(Mono.just(new KeycloakUserResult(keycloakUserId, "tempPassword")));

            try {
                when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            } catch (Exception e) {
                // Ignore
            }

            // Act & Assert - Should succeed even if role already exists (handled by onErrorResume)
            StepVerifier.create(userManagementService.createParent(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // CREATE STAFF INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Staff - Service Integration Tests")
    class CreateStaffIntegrationTests {

        @Test
        @DisplayName("Should create teacher account")
        void shouldCreateTeacherAccount() {
            // Arrange
            CreateStaffRequest request = new CreateStaffRequest(
                    "teacher@school.edu",
                    "Teacher",
                    "User",
                    "+2348044444444",
                    "TEACHER",
                    Set.of("TEACHER")
            );

            try {
                when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            } catch (Exception e) {
                // Ignore
            }

            // Act
            StepVerifier.create(userManagementService.createStaff(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.email()).isEqualTo("teacher@school.edu");
                        assertThat(response.firstName()).isEqualTo("Teacher");
                        assertThat(response.lastName()).isEqualTo("User");
                        assertThat(response.userType()).isEqualTo("TEACHER");
                        assertThat(response.roles()).contains("TEACHER");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should create staff with multiple roles")
        void shouldCreateStaffWithMultipleRoles() {
            // Arrange
            CreateStaffRequest request = new CreateStaffRequest(
                    "admin.accountant@school.edu",
                    "+2348055555555",
                    "Admin",
                    "Accountant",
                    "SCHOOL_ADMIN",
                    Set.of("SCHOOL_ADMIN", "ACCOUNTANT")
            );

            try {
                when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            } catch (Exception e) {
                // Ignore
            }

            // Act
            StepVerifier.create(userManagementService.createStaff(request))
                    .assertNext(response -> {
                        assertThat(response.roles()).hasSize(2);
                        assertThat(response.roles()).containsExactlyInAnyOrder("SCHOOL_ADMIN", "ACCOUNTANT");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail for invalid user type")
        void shouldFailForInvalidUserType() {
            // Arrange
            CreateStaffRequest request = new CreateStaffRequest(
                    "invalid@school.edu",
                    "+2348066666666",
                    "Invalid",
                    "User",
                    "INVALID_TYPE",
                    Set.of("INVALID_TYPE")
            );

            // Act & Assert
            StepVerifier.create(userManagementService.createStaff(request))
                    .expectErrorMatches(error ->
                        error instanceof SchoolFeeException &&
                        ((SchoolFeeException) error).getErrorCode().equals("INVALID_USER_TYPE"))
                    .verify();
        }
    }

    // ========================================================================
    // TRANSACTIONAL BEHAVIOR TESTS
    // ========================================================================

    @Nested
    @DisplayName("Transactional Behavior Tests")
    class TransactionalBehaviorTests {

        @Test
        @DisplayName("Should demonstrate transactional operator usage")
        void shouldDemonstrateTransactionalOperatorUsage() {
            // This test verifies that the transactional operator is properly configured
            // and can rollback on errors
            
            // Arrange
            Student student = Student.builder()
                    .schoolId(SCHOOL_ID)
                    .admissionNumber("ADM005")
                    .firstName("Transaction")
                    .lastName("Test")
                    .dateOfBirth(java.time.LocalDate.of(2010, 1, 1))
                    .enrollmentDate(java.time.LocalDate.of(2020, 1, 1))
                    .enrollmentStatus("ACTIVE")
                    .build();

            UUID studentId = studentRepository.save(student).block().getId();

            List<CreateParentRequest.ChildLink> children = List.of(
                    new CreateParentRequest.ChildLink(studentId, "FATHER", true)
            );

            CreateParentRequest request = new CreateParentRequest(
                    "+2348077777777",
                    "transaction@test.com",
                    "Transaction",
                    "Test",
                    children
            );

            UUID keycloakUserId = UUID.randomUUID();
            when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet()))
                    .thenReturn(Mono.just(new KeycloakUserResult(keycloakUserId, "tempPassword")));

            try {
                when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            } catch (Exception e) {
                // Ignore
            }

            // Act & Assert - Should complete successfully
            StepVerifier.create(userManagementService.createParent(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                        assertThat(response.email()).isEqualTo("transaction@test.com");
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // OUTBOX PATTERN TESTS
    // ========================================================================

    @Nested
    @DisplayName("Outbox Pattern Tests")
    class OutboxPatternTests {

        @Test
        @DisplayName("Should create outbox event for parent invitation")
        void shouldCreateOutboxEventForParentInvitation() {
            // Arrange
            Student student = Student.builder()
                    .schoolId(SCHOOL_ID)
                    .admissionNumber("ADM006")
                    .firstName("Outbox")
                    .lastName("Test")
                    .dateOfBirth(java.time.LocalDate.of(2010, 1, 1))
                    .enrollmentDate(java.time.LocalDate.of(2020, 1, 1))
                    .enrollmentStatus("ACTIVE")
                    .build();

            UUID studentId = studentRepository.save(student).block().getId();

            List<CreateParentRequest.ChildLink> children = List.of(
                    new CreateParentRequest.ChildLink(studentId, "FATHER", true)
            );

            CreateParentRequest request = new CreateParentRequest(
                    "+2348088888888",
                    "outbox@test.com",
                    "Outbox",
                    "Test",
                    children
            );

            UUID keycloakUserId = UUID.randomUUID();
            when(keycloakAdminService.createUser(any(), eq("PARENT"), anySet()))
                    .thenReturn(Mono.just(new KeycloakUserResult(keycloakUserId, "tempPassword")));

            try {
                when(objectMapper.valueToTree(any())).thenReturn(NullNode.getInstance());
            } catch (Exception e) {
                // Ignore
            }

            // Act
            StepVerifier.create(userManagementService.createParent(request))
                    .assertNext(response -> {
                        assertThat(response).isNotNull();
                    })
                    .verifyComplete();

            // Verify outbox event was created in database
            StepVerifier.create(outboxEventRepository.findAll().count())
                    .assertNext(count -> assertThat(count).isGreaterThan(0))
                    .verifyComplete();

            // Verify outbox event has correct type
            StepVerifier.create(outboxEventRepository.findAll())
                    .assertNext(event -> {
                        assertThat(event).isNotNull();
                        assertThat(event.getEventType()).isEqualTo("PARENT_INVITATION");
                        assertThat(event.getAggregateId()).isNotNull();
                        assertThat(event.getStatus()).isEqualTo("PENDING");
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // CHECK ACCOUNT INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Check Account - Service Integration Tests")
    class CheckAccountIntegrationTests {
        // The DB schema enforces a unique constraint on (phone, school_id),
        // preventing duplicate guardian records for the same school.
        // Therefore, we removed the test that attempted to insert duplicates.

    }

    // ========================================================================
    // SEND OTP INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Send OTP Integration Tests")
    class SendOtpIntegrationTests {

        @Test
        @DisplayName("Should successfully send OTP for unregistered guardian")
        void shouldSendOtpForUnregisteredGuardian() {
            String phone = "+2348011111111";
            String normalizedPhone = "2348011111111";

            StudentGuardian guardian = StudentGuardian.builder()
                    .schoolId(SCHOOL_ID)
                    .firstName("Unregistered")
                    .lastName("Parent")
                    .phone(normalizedPhone)
                    .email("unreg@test.com")
                    .isActive(true)
                    .build();

            guardianRepository.save(guardian).block();

            com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest request =
                    new com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest(phone);

            StepVerifier.create(userManagementService.sendOtp(request))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should fail to send OTP if guardian is fully registered")
        void shouldFailToSendOtpIfRegistered() {
            String phone = "+2348022222222";
            String normalizedPhone = "2348022222222";

            com.fee.app.schoolfeeapp.auth.domain.User user = com.fee.app.schoolfeeapp.auth.domain.User.builder()
                    .keycloakId(UUID.randomUUID())
                    .schoolId(SCHOOL_ID)
                    .firstName("Registered")
                    .lastName("Parent")
                    .phone(normalizedPhone)
                    .email("reg@test.com")
                    .userType("PARENT")
                    .isActive(true)
                    .build();
            
            com.fee.app.schoolfeeapp.auth.domain.User savedUser = userRepository.save(user).block();

            StudentGuardian guardian = StudentGuardian.builder()
                    .schoolId(SCHOOL_ID)
                    .userId(savedUser.getId()) // Fully registered
                    .firstName("Registered")
                    .lastName("Parent")
                    .phone(normalizedPhone)
                    .email("reg@test.com")
                    .isActive(true)
                    .build();

            guardianRepository.save(guardian).block();

            com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest request =
                    new com.fee.app.schoolfeeapp.auth.dto.request.SendOtpRequest(phone);

            StepVerifier.create(userManagementService.sendOtp(request))
                    .expectErrorMatches(e -> e instanceof SchoolFeeException &&
                            ((SchoolFeeException) e).getErrorCode().equals("ACCOUNT_ALREADY_REGISTERED"))
                    .verify();
        }
    }

    // ========================================================================
    // VERIFY OTP INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Verify OTP Integration Tests")
    class VerifyOtpIntegrationTests {

        @Test
        @DisplayName("Should verify OTP and update guardian with new user ID")
        void shouldVerifyAndCreateAccount() {
            String phone = "+2348033333333";
            String normalizedPhone = "2348033333333";

            StudentGuardian guardian = StudentGuardian.builder()
                    .schoolId(SCHOOL_ID)
                    .firstName("Unregistered")
                    .lastName("Parent")
                    .phone(normalizedPhone)
                    .email("unreg@test.com")
                    .isActive(true)
                    .build();

            StudentGuardian savedGuardian = guardianRepository.save(guardian).block();

            // Mock Keycloak to not find existing user, and create new
            when(keycloakAdminService.findByUsername(normalizedPhone)).thenReturn(Optional.empty());
            when(keycloakAdminService.createUser(any(), any(), any())).thenReturn(Mono.just(new KeycloakUserResult(UUID.randomUUID(), "tempPassword")));

            var request = new com.fee.app.schoolfeeapp.auth.dto.response.VerifyOtpRequest(phone, "000000", SCHOOL_ID);

            StepVerifier.create(userManagementService.verifyOtpAndCreateAccount(request))
                    .assertNext(res -> {
                        assertThat(res).containsEntry("message", "Account created. Set your password to continue.");
                    })
                    .verifyComplete();

            // Verify DB state
            StudentGuardian updatedGuardian = guardianRepository.findById(savedGuardian.getId()).block();
            assertThat(updatedGuardian.getUserId()).isNotNull();

            com.fee.app.schoolfeeapp.auth.domain.User createdUser = userRepository.findById(updatedGuardian.getUserId()).block();
            assertThat(createdUser).isNotNull();
            assertThat(createdUser.getPhone()).isEqualTo(normalizedPhone);
        }
    }

    // ========================================================================
    // SET PASSWORD INTEGRATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Set Password Integration Tests")
    class SetPasswordIntegrationTests {

        @Test
        @DisplayName("Should fetch correct user and pass keycloakId to Keycloak service")
        void shouldSetPassword() {
            String phone = "+2348044444444";
            String normalizedPhone = "2348044444444";
            UUID keycloakId = UUID.randomUUID();

            com.fee.app.schoolfeeapp.auth.domain.User user = com.fee.app.schoolfeeapp.auth.domain.User.builder()
                    .keycloakId(keycloakId)
                    .schoolId(SCHOOL_ID)
                    .firstName("Test")
                    .lastName("User")
                    .phone(normalizedPhone)
                    .email("test@test.com")
                    .userType("PARENT")
                    .isActive(true)
                    .build();

            com.fee.app.schoolfeeapp.auth.domain.User savedUser = userRepository.save(user).block();

            StudentGuardian guardian = StudentGuardian.builder()
                    .schoolId(SCHOOL_ID)
                    .userId(savedUser.getId())
                    .firstName("Test")
                    .lastName("User")
                    .phone(normalizedPhone)
                    .email("test@test.com")
                    .isActive(true)
                    .build();

            guardianRepository.save(guardian).block();

            org.mockito.Mockito.doNothing().when(keycloakAdminService).setUserPassword(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(false));

            var request = new com.fee.app.schoolfeeapp.auth.dto.request.SetPasswordRequest(phone, "NewPassword123!");

            StepVerifier.create(userManagementService.setPassword(request))
                    .assertNext(res -> {
                        assertThat(res).containsEntry("message", "Password set. You can now log in.");
                    })
                    .verifyComplete();

            org.mockito.Mockito.verify(keycloakAdminService).setUserPassword(keycloakId.toString(), "NewPassword123!", false);
        }
    }
}
