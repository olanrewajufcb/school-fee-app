package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fee.app.schoolfeeapp.auth.domain.User;
import com.fee.app.schoolfeeapp.auth.domain.UserSchoolRole;
import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.repository.UserSchoolRoleRepository;
import com.fee.app.schoolfeeapp.auth.service.GuardianLinkingService;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSchoolRoleRepository roleRepository;

    @Mock
    private GuardianLinkingService guardianLinkingService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private TransactionalOperator transactionalOperator;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final UUID KEYCLOAK_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID USER_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID SCHOOL_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final String SCHOOL_NAME = "Grace International School";

    private SchoolFeeUser parentJwtUser;
    private SchoolFeeUser adminJwtUser;
    private User existingUser;

    @BeforeEach
    void setUp() {
        // Setup parent JWT user
        parentJwtUser = SchoolFeeUser.builder()
                .userId(KEYCLOAK_ID)
                .email("parent@example.com")
                .phoneNumber("+2348012345678")
                .firstName("John")
                .lastName("Doe")
                .userType("PARENT")
                .schoolId(SCHOOL_ID)
                .schoolName(SCHOOL_NAME)
                .roles(Set.of("PARENT"))
                .build();

        // Setup admin JWT user
        adminJwtUser = SchoolFeeUser.builder()
                .userId(KEYCLOAK_ID)
                .email("admin@example.com")
                .phoneNumber("+2348098765432")
                .firstName("Admin")
                .lastName("User")
                .userType("SCHOOL_ADMIN")
                .schoolId(SCHOOL_ID)
                .schoolName(SCHOOL_NAME)
                .roles(Set.of("SCHOOL_ADMIN", "ACCOUNTANT"))
                .build();

        // Setup existing user
        existingUser = User.builder()
                .id(USER_ID)
                .keycloakId(KEYCLOAK_ID)
                .schoolId(SCHOOL_ID)
                .email("parent@example.com")
                .phone("2348012345678")
                .firstName("John")
                .lastName("Doe")
                .userType("PARENT")
                .isActive(true)
                .lastLogin(ZonedDateTime.now().minusHours(1))
                .build();

        // Mock transactionalOperator to pass through by default
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ========================================================================
    // GET CURRENT USER PROFILE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Get Current User Profile")
    class GetCurrentUserProfileTests {

        @Test
        @DisplayName("Should get profile for existing parent user with children")
        void shouldGetProfileForExistingParentWithChildren() {
            // Arrange
            List<UserProfileResponse.ChildInfo> children = List.of(
                    UserProfileResponse.ChildInfo.builder()
                            .studentId(UUID.randomUUID())
                            .guardianId(UUID.randomUUID())
                            .relationship("FATHER")
                            .canViewFees(true)
                            .canViewResults(true)
                            .canViewAttendance(true)
                            .build()
            );

            UserSchoolRole userRole = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("PARENT")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(existingUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.just(userRole));
            when(guardianLinkingService.getOrLinkGuardian(eq(existingUser), eq(parentJwtUser)))
                    .thenReturn(Mono.just(children));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserId()).isEqualTo(USER_ID);
                        assertThat(profile.getKeycloakId()).isEqualTo(KEYCLOAK_ID);
                        assertThat(profile.getEmail()).isEqualTo("parent@example.com");
                        assertThat(profile.getPhoneNumber()).isEqualTo("+2348012345678");
                        assertThat(profile.getFirstName()).isEqualTo("John");
                        assertThat(profile.getLastName()).isEqualTo("Doe");
                        assertThat(profile.getUserType()).isEqualTo("PARENT");
                        assertThat(profile.getSchoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(profile.getSchoolName()).isEqualTo(SCHOOL_NAME);
                        assertThat(profile.getRoles()).containsExactlyInAnyOrder("PARENT");
                        assertThat(profile.getChildren()).hasSize(1);
                        assertThat(profile.isActive()).isTrue();
                        assertThat(profile.getLastLogin()).isNotNull();
                    })
                    .verifyComplete();

            verify(jwtUtils, times(1)).getCurrentUser();
            verify(userRepository, times(1)).findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID);
            verify(guardianLinkingService, times(1)).getOrLinkGuardian(eq(existingUser), eq(parentJwtUser));
        }

        @Test
        @DisplayName("Should get profile for existing admin user without children")
        void shouldGetProfileForExistingAdminWithoutChildren() {
            // Arrange
            User adminUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("admin@example.com")
                    .phone("2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now().minusDays(1))
                    .build();

            UserSchoolRole userRole = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("ACCOUNTANT")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(adminJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(adminUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(existingUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.just(userRole));
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "SCHOOL_ADMIN"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserType()).isEqualTo("SCHOOL_ADMIN");
                        assertThat(profile.getRoles()).containsExactlyInAnyOrder("SCHOOL_ADMIN", "ACCOUNTANT");
                        assertThat(profile.getChildren()).isEmpty();
                    })
                    .verifyComplete();

            verify(guardianLinkingService, never()).getOrLinkGuardian(any(), any());
        }

        @Test
        @DisplayName("Should create new user if not found (first login)")
        void shouldCreateNewUserIfNotFound() {
            // Arrange
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.empty());

            User newUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            when(userRepository.save(any(User.class))).thenReturn(Mono.just(newUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "PARENT"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(guardianLinkingService.getOrLinkGuardian(eq(newUser), eq(parentJwtUser)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserId()).isEqualTo(USER_ID);
                        assertThat(profile.getEmail()).isEqualTo("parent@example.com");
                    })
                    .verifyComplete();

            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Should update last login if older than 15 minutes")
        void shouldUpdateLastLoginIfOlderThan15Minutes() {
            // Arrange
            User userWithOldLogin = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now().minusHours(2)) // Old login
                    .build();

            User updatedUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now()) // Updated login
                    .build();

            UserSchoolRole userRole = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("PARENT")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(eq(KEYCLOAK_ID)))
                    .thenReturn(Mono.just(userWithOldLogin));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.just(userRole));
            when(guardianLinkingService.getOrLinkGuardian(any(User.class), eq(parentJwtUser)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getLastLogin()).isNotNull();
                    })
                    .verifyComplete();

            // Verify findByKeycloakId was called
            verify(userRepository, times(1)).findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID);
            
            verify(userRepository, times(1)).save(argThat(user ->
                user.getLastLogin() != null &&
                user.getLastLogin().isAfter(ZonedDateTime.now().minusMinutes(1))
            ));
        }

        @Test
        @DisplayName("Should NOT update last login if within 15 minutes")
        void shouldNotUpdateLastLoginIfWithin15Minutes() {
            // Arrange
            User userWithRecentLogin = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now().minusMinutes(5)) // Recent login
                    .build();

            UserSchoolRole userRole = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("PARENT")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(userWithRecentLogin));

            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.just(userRole));

            when(guardianLinkingService.getOrLinkGuardian(eq(userWithRecentLogin), eq(parentJwtUser)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getLastLogin()).isNotNull();
                    })
                    .verifyComplete();

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should sync roles from JWT to database - add new roles")
        void shouldSyncRolesFromJwtToAddNewRoles() {
            // Arrange
            SchoolFeeUser userWithNewRoles = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("admin@example.com")
                    .phoneNumber("+2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Set.of("SCHOOL_ADMIN", "ACCOUNTANT", "TEACHER")) // TEACHER is new
                    .build();

            User adminUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("admin@example.com")
                    .phone("2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            // Existing roles in DB
            UserSchoolRole existingRole1 = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("SCHOOL_ADMIN")
                    .isActive(true)
                    .build();

            UserSchoolRole existingRole2 = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("ACCOUNTANT")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(userWithNewRoles));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(adminUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID))
                    .thenReturn(Flux.just(existingRole1, existingRole2));
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "TEACHER"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getRoles()).containsExactlyInAnyOrder("SCHOOL_ADMIN", "ACCOUNTANT", "TEACHER");
                    })
                    .verifyComplete();

            // Verify new role was created
            verify(roleRepository, times(1)).save(argThat(role -> 
                "TEACHER".equals(role.getRole()) && role.getIsActive()
            ));
        }

        @Test
        @DisplayName("Should sync roles from JWT to database - deactivate removed roles")
        void shouldSyncRolesFromJwtToDeactivateRemovedRoles() {
            // Arrange
            SchoolFeeUser existingSchoolAdminRole = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("admin@example.com")
                    .phoneNumber("+2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Set.of("SCHOOL_ADMIN")) // ACCOUNTANT removed
                    .build();

            User adminUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("admin@example.com")
                    .phone("2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            UserSchoolRole roleToRemove = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("ACCOUNTANT")
                    .isActive(true)
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(existingSchoolAdminRole));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(adminUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID))
                    .thenReturn(Flux.just(roleToRemove));
            when(roleRepository.findByUserIdAndSchoolIdAndRole(
                    USER_ID, SCHOOL_ID, "SCHOOL_ADMIN"))
                    .thenReturn(Mono.empty());

            when(roleRepository.findByUserIdAndSchoolIdAndRoleAndIsActiveTrue(USER_ID, SCHOOL_ID, "ACCOUNTANT"))
                    .thenReturn(Mono.just(roleToRemove));
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getRoles()).containsExactly("SCHOOL_ADMIN");
                    })
                    .verifyComplete();

            // Verify role was deactivated
            verify(roleRepository, times(1)).save(argThat(role -> 
                "ACCOUNTANT".equals(role.getRole()) && !role.getIsActive()
            ));
        }

        @Test
        @DisplayName("Should reactivate previously deactivated role")
        void shouldReactivatePreviouslyDeactivatedRole() {
            // Arrange
            SchoolFeeUser userWithReactivatedRole = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("admin@example.com")
                    .phoneNumber("+2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Set.of("SCHOOL_ADMIN", "ACCOUNTANT")) // ACCOUNTANT reactivated
                    .build();

            User adminUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("admin@example.com")
                    .phone("2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            UserSchoolRole deactivatedRole = UserSchoolRole.builder()
                    .id(UUID.randomUUID())
                    .userId(USER_ID)
                    .schoolId(SCHOOL_ID)
                    .role("ACCOUNTANT")
                    .isActive(false) // Previously deactivated
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(userWithReactivatedRole));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(adminUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID))
                    .thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "SCHOOL_ADMIN"))
                    .thenReturn(Mono.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "ACCOUNTANT"))
                    .thenReturn(Mono.just(deactivatedRole));
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getRoles()).contains("ACCOUNTANT");
                    })
                    .verifyComplete();

            // Verify role was reactivated
            verify(roleRepository, times(1)).save(argThat(role -> 
                "ACCOUNTANT".equals(role.getRole()) && role.getIsActive()
            ));
        }

        @Test
        @DisplayName("Should handle race condition when creating user")
        void shouldHandleRaceConditionWhenCreatingUser() {
            // Arrange

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));

            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID))
                    .thenReturn(Flux.empty());

            when(roleRepository.findByUserIdAndSchoolIdAndRole(
                    eq(USER_ID),
                    eq(SCHOOL_ID),
                    eq("PARENT")))
                    .thenReturn(Mono.empty());

            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            // First save fails with duplicate key
            when(userRepository.save(any(User.class)))
                    .thenReturn(Mono.error(new DuplicateKeyException("duplicate")));

            // Second find succeeds (concurrent request created it)
            User concurrentUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.empty())
                    .thenReturn(Mono.just(concurrentUser));
            when(guardianLinkingService.getOrLinkGuardian(eq(concurrentUser), eq(parentJwtUser)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserId()).isEqualTo(USER_ID);
                    })
                    .verifyComplete();

            verify(userRepository, times(1)).save(any(User.class));
            verify(userRepository, times(2)).findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID);
        }

        @Test
        @DisplayName("Should handle invalid phone number gracefully")
        void shouldHandleInvalidPhoneNumberGracefully() {
            // Arrange
            SchoolFeeUser userWithInvalidPhone = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("parent@example.com")
                    .phoneNumber("invalid-phone")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Set.of("PARENT"))
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(userWithInvalidPhone));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.empty());
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID))
                    .thenReturn(Flux.empty());

            when(roleRepository.findByUserIdAndSchoolIdAndRole(
                    eq(USER_ID),
                    eq(SCHOOL_ID),
                    eq("PARENT")))
                    .thenReturn(Mono.empty());

            User newUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone(null) // Invalid phone becomes null
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            when(userRepository.save(any(User.class))).thenReturn(Mono.just(newUser));
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(guardianLinkingService.getOrLinkGuardian(eq(newUser), eq(userWithInvalidPhone)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getPhoneNumber()).isEqualTo("invalid-phone"); // Original phone from JWT
                    })
                    .verifyComplete();

            // Verify user was saved with null phone
            verify(userRepository).save(argThat(user -> user.getPhone() == null));
        }

        @Test
        @DisplayName("Should fail if user creation race condition recovery fails")
        void shouldFailIfUserCreationRaceConditionRecoveryFails() {
            // Arrange
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.empty());
            when(userRepository.save(any(User.class)))
                    .thenReturn(Mono.error(new DuplicateKeyException("duplicate")));
            
            // Second find also fails
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.empty());

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should handle empty roles in JWT")
        void shouldHandleEmptyRolesInJwt() {
            // Arrange
            SchoolFeeUser userWithNoRoles = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("parent@example.com")
                    .phoneNumber("+2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Collections.emptySet())
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(userWithNoRoles));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(existingUser));
            when(guardianLinkingService.getOrLinkGuardian(eq(existingUser), eq(userWithNoRoles)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getRoles()).isEmpty();
                    })
                    .verifyComplete();

            // Should not query or modify roles
            verify(roleRepository, never()).findByUserIdAndSchoolId(any(), any());
        }

        @Test
        @DisplayName("Should handle parent with no children")
        void shouldHandleParentWithNoChildren() {
            // Arrange
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(existingUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "PARENT"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(guardianLinkingService.getOrLinkGuardian(eq(existingUser), eq(parentJwtUser)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserType()).isEqualTo("PARENT");
                        assertThat(profile.getChildren()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle multiple children for parent")
        void shouldHandleMultipleChildrenForParent() {
            // Arrange
            List<UserProfileResponse.ChildInfo> multipleChildren = List.of(
                    UserProfileResponse.ChildInfo.builder()
                            .studentId(UUID.randomUUID())
                            .guardianId(UUID.randomUUID())
                            .relationship("FATHER")
                            .canViewFees(true)
                            .canViewResults(true)
                            .canViewAttendance(true)
                            .build(),
                    UserProfileResponse.ChildInfo.builder()
                            .studentId(UUID.randomUUID())
                            .guardianId(UUID.randomUUID())
                            .relationship("MOTHER")
                            .canViewFees(true)
                            .canViewResults(false)
                            .canViewAttendance(true)
                            .build()
            );

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(existingUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "PARENT"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(guardianLinkingService.getOrLinkGuardian(eq(existingUser), eq(parentJwtUser)))
                    .thenReturn(Mono.just(multipleChildren));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getChildren()).hasSize(2);
                        assertThat(profile.getChildren().get(0).getRelationship()).isEqualTo("FATHER");
                        assertThat(profile.getChildren().get(1).getRelationship()).isEqualTo("MOTHER");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should execute within transaction")
        void shouldExecuteWithinTransaction() {
            // Arrange
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(existingUser));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(existingUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "PARENT"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(guardianLinkingService.getOrLinkGuardian(eq(existingUser), eq(parentJwtUser)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();

            // Verify transactional operator was called
            verify(transactionalOperator, times(1)).transactional(any(Mono.class));
        }
    }

    // ========================================================================
    // EDGE CASES AND ERROR HANDLING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle null JWT user")
        void shouldHandleNullJwtUser() {
            // Arrange
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.empty());

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .expectComplete() // Mono.empty() completes without emitting
                    .verify();
        }

        @Test
        @DisplayName("Should handle user with null last login")
        void shouldHandleUserWithNullLastLogin() {
            // Arrange
            User userWithNullLogin = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(null) // Null last login
                    .build();

            User updatedUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("parent@example.com")
                    .phone("2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("PARENT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(parentJwtUser));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(userWithNullLogin));
            when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "PARENT"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(guardianLinkingService.getOrLinkGuardian(eq(updatedUser), eq(parentJwtUser)))
                    .thenReturn(Mono.just(Collections.emptyList()));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getLastLogin()).isNotNull();
                    })
                    .verifyComplete();

            // Should update last login
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Should handle super admin user type")
        void shouldHandleSuperAdminUserType() {
            // Arrange
            SchoolFeeUser superAdmin = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("superadmin@example.com")
                    .phoneNumber("+2348000000000")
                    .firstName("Super")
                    .lastName("Admin")
                    .userType("SUPER_ADMIN")
                    .schoolId(null) // Super admin has no school
                    .schoolName(null)
                    .roles(Set.of("SUPER_ADMIN"))
                    .build();

            User superAdminUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(null)
                    .email("superadmin@example.com")
                    .phone("2348000000000")
                    .firstName("Super")
                    .lastName("Admin")
                    .userType("SUPER_ADMIN")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(superAdmin));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(superAdminUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, null)).thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, null, "SUPER_ADMIN"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserType()).isEqualTo("SUPER_ADMIN");
                        assertThat(profile.getSchoolId()).isNull();
                        assertThat(profile.getSchoolName()).isNull();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle teacher user type")
        void shouldHandleTeacherUserType() {
            // Arrange
            SchoolFeeUser teacher = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("teacher@example.com")
                    .phoneNumber("+2348011111111")
                    .firstName("Jane")
                    .lastName("Teacher")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Set.of("TEACHER"))
                    .build();

            User teacherUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("teacher@example.com")
                    .phone("2348011111111")
                    .firstName("Jane")
                    .lastName("Teacher")
                    .userType("TEACHER")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(teacher));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(teacherUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID)).thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "TEACHER"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserType()).isEqualTo("TEACHER");
                        assertThat(profile.getChildren()).isEmpty(); // Teachers don't have children
                    })
                    .verifyComplete();

            verify(guardianLinkingService, never()).getOrLinkGuardian(any(), any());
        }

        @Test
        @DisplayName("Should handle accountant user type")
        void shouldHandleAccountantUserType() {
            // Arrange
            SchoolFeeUser accountant = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("accountant@example.com")
                    .phoneNumber("+2348022222222")
                    .firstName("Bob")
                    .lastName("Accountant")
                    .userType("ACCOUNTANT")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Set.of("ACCOUNTANT"))
                    .build();

            User accountantUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("accountant@example.com")
                    .phone("2348022222222")
                    .firstName("Bob")
                    .lastName("Accountant")
                    .userType("ACCOUNTANT")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(accountant));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(accountantUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID))
                    .thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(USER_ID, SCHOOL_ID, "ACCOUNTANT"))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getUserType()).isEqualTo("ACCOUNTANT");
                        assertThat(profile.getChildren()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should handle concurrent role synchronization")
        void shouldHandleConcurrentRoleSynchronization() {
            // Arrange
            SchoolFeeUser userWithRoles = SchoolFeeUser.builder()
                    .userId(KEYCLOAK_ID)
                    .email("admin@example.com")
                    .phoneNumber("+2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .schoolId(SCHOOL_ID)
                    .schoolName(SCHOOL_NAME)
                    .roles(Set.of("SCHOOL_ADMIN", "ACCOUNTANT"))
                    .build();

            User adminUser = User.builder()
                    .id(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .schoolId(SCHOOL_ID)
                    .email("admin@example.com")
                    .phone("2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .isActive(true)
                    .lastLogin(ZonedDateTime.now())
                    .build();

            // No existing roles
            when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(userWithRoles));
            when(userRepository.findByKeycloakIdAndDeletedAtIsNull(KEYCLOAK_ID))
                    .thenReturn(Mono.just(adminUser));
            when(roleRepository.findByUserIdAndSchoolId(USER_ID, SCHOOL_ID))
                    .thenReturn(Flux.empty());
            when(roleRepository.findByUserIdAndSchoolIdAndRole(any(), any(), anyString()))
                    .thenReturn(Mono.empty());
            when(roleRepository.save(any(UserSchoolRole.class)))
                    .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            // Act
            Mono<UserProfileResponse> result = authService.getCurrentUserProfile();

            // Assert
            StepVerifier.create(result)
                    .assertNext(profile -> {
                        assertThat(profile.getRoles()).hasSize(2);
                    })
                    .verifyComplete();

            // Both roles should be created
            verify(roleRepository, times(2)).save(any(UserSchoolRole.class));
        }
    }
}
