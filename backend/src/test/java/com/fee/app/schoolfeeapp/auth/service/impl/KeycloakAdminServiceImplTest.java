package com.fee.app.schoolfeeapp.auth.service.impl;

import com.fee.app.schoolfeeapp.auth.dto.request.CreateStaffRequest;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceImplTest {

    @Mock
    private Keycloak keycloak;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    @Mock
    private RolesResource rolesResource;

    @Mock
    private RoleResource roleResource;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private org.keycloak.admin.client.resource.RoleMappingResource roleMappingResource;

    @Mock
    private org.keycloak.admin.client.resource.RoleScopeResource roleScopeResource;

    @Mock
    private Response response;

    private KeycloakAdminServiceImpl keycloakAdminService;

    private static final String REALM = "test-realm";
    private static final String SERVER_URL = "http://localhost:8080/auth";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin";
    private static final String ADMIN_CLIENT_ID = "admin-cli";
    private static final String USER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String EMAIL = "test@example.com";
    private static final String PHONE = "+2348012345678";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final String SCHOOL_NAME = "Test School";

    @BeforeEach
    void setUp() {
        // Create the service with mocked Keycloak client using package-private constructor
        keycloakAdminService = new KeycloakAdminServiceImpl(keycloak, REALM);

        // Setup common mocks
        when(keycloak.realm(REALM)).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
    }

    // ========================================================================
    // CREATE USER TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create User Tests")
    class CreateUserTests {

        @Test
        @DisplayName("Should create user successfully with roles")
        void shouldCreateUserSuccessfullyWithRoles() {
            // Arrange
            UserRepresentation userRep = new UserRepresentation();
            userRep.setUsername(EMAIL);
            userRep.setEmail(EMAIL);
            userRep.setFirstName(FIRST_NAME);
            userRep.setLastName(LAST_NAME);

            Set<String> roles = Set.of("PARENT");

            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
            when(response.getStatus()).thenReturn(201);
            when(response.getHeaderString("Location"))
                    .thenReturn("http://localhost:8080/auth/realms/" + REALM + "/users/" + USER_ID);

            // Mock password reset
            doNothing().when(userResource).resetPassword(any(CredentialRepresentation.class));
            when(usersResource.get(USER_ID)).thenReturn(userResource);

            // Mock role assignment
            when(realmResource.roles()).thenReturn(rolesResource);
            when(rolesResource.get("PARENT")).thenReturn(roleResource);
            RoleRepresentation parentRole = new RoleRepresentation();
            parentRole.setName("PARENT");
            when(roleResource.toRepresentation()).thenReturn(parentRole);
            
            // Mock user roles resource for role assignment
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
            doNothing().when(roleScopeResource).add(anyList());

            // Act
            Mono<UUID> result = keycloakAdminService.createUser(userRep, "PARENT", roles);

            // Assert
            StepVerifier.create(result)
                    .assertNext(uuid -> {
                        assertThat(uuid).isEqualTo(UUID.fromString(USER_ID));
                        assertThat(userRep.isEnabled()).isTrue();
                        assertThat(userRep.getAttributes()).containsKey("user_type");
                        assertThat(userRep.getAttributes().get("user_type")).contains("PARENT");
                    })
                    .verifyComplete();

            verify(usersResource).create(userRep);
            verify(userResource).resetPassword(any(CredentialRepresentation.class));
        }

        @Test
        @DisplayName("Should throw exception when user creation fails")
        void shouldThrowExceptionWhenUserCreationFails() {
            // Arrange
            UserRepresentation userRep = new UserRepresentation();
            userRep.setUsername(EMAIL);

            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
            when(response.getStatus()).thenReturn(409);
            when(response.readEntity(String.class)).thenReturn("User already exists");

            // Act
            Mono<UUID> result = keycloakAdminService.createUser(userRep, "PARENT", Set.of());

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();

            verify(response).close();
        }

        @Test
        @DisplayName("Should handle missing Location header gracefully")
        void shouldHandleMissingLocationHeaderGracefully() {
            // Arrange
            UserRepresentation userRep = new UserRepresentation();
            userRep.setUsername(EMAIL);

            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
            when(response.getStatus()).thenReturn(201);
            when(response.getHeaderString("Location")).thenReturn(null);

            // Act
            Mono<UUID> result = keycloakAdminService.createUser(userRep, "PARENT", Set.of());

            // Assert
            StepVerifier.create(result)
                    .expectError(SchoolFeeException.class)
                    .verify();
        }

        @Test
        @DisplayName("Should skip non-existent roles gracefully")
        void shouldSkipNonExistentRolesGracefully() {
            // Arrange
            UserRepresentation userRep = new UserRepresentation();
            userRep.setUsername(EMAIL);
            Set<String> roles = Set.of("PARENT", "NON_EXISTENT_ROLE");

            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
            when(response.getStatus()).thenReturn(201);
            when(response.getHeaderString("Location"))
                    .thenReturn("http://localhost:8080/auth/realms/" + REALM + "/users/" + USER_ID);

            when(usersResource.get(USER_ID)).thenReturn(userResource);
            when(realmResource.roles()).thenReturn(rolesResource);
            
            // First role exists
            when(rolesResource.get("PARENT")).thenReturn(roleResource);
            RoleRepresentation parentRole = new RoleRepresentation();
            parentRole.setName("PARENT");
            when(roleResource.toRepresentation()).thenReturn(parentRole);
            
            // Second role doesn't exist (throws exception)
            when(rolesResource.get("NON_EXISTENT_ROLE")).thenThrow(new RuntimeException("Role not found"));

            doNothing().when(userResource).resetPassword(any(CredentialRepresentation.class));
            
            // Mock user roles resource for role assignment
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
            doNothing().when(roleScopeResource).add(anyList());

            // Act
            Mono<UUID> result = keycloakAdminService.createUser(userRep, "PARENT", roles);

            // Assert
            StepVerifier.create(result)
                    .assertNext(uuid -> assertThat(uuid).isEqualTo(UUID.fromString(USER_ID)))
                    .verifyComplete();
        }
    }

    // ========================================================================
    // CREATE STAFF USER TESTS
    // ========================================================================

    @Nested
    @DisplayName("Create Staff User Tests")
    class CreateStaffUserTests {

        @Test
        @DisplayName("Should create staff user successfully")
        void shouldCreateStaffUserSuccessfully() {
            // Arrange
            CreateStaffRequest request = new CreateStaffRequest(
                    EMAIL,
                    FIRST_NAME,
                    LAST_NAME,
                    PHONE,
                    "SCHOOL_ADMIN",
                    Set.of("SCHOOL_ADMIN")
            );

            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
            when(response.getStatus()).thenReturn(201);
            when(response.getHeaderString("Location"))
                    .thenReturn("http://localhost:8080/auth/realms/" + REALM + "/users/" + USER_ID);

            when(usersResource.get(USER_ID)).thenReturn(userResource);
            doNothing().when(userResource).resetPassword(any(CredentialRepresentation.class));

            when(realmResource.roles()).thenReturn(rolesResource);
            when(rolesResource.get("SCHOOL_ADMIN")).thenReturn(roleResource);
            RoleRepresentation adminRole = new RoleRepresentation();
            adminRole.setName("SCHOOL_ADMIN");
            when(roleResource.toRepresentation()).thenReturn(adminRole);
            
            // Mock user roles resource for role assignment
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
            doNothing().when(roleScopeResource).add(anyList());

            // Act
            Mono<UUID> result = keycloakAdminService.createStaffUser(request, SCHOOL_ID, SCHOOL_NAME);

            // Assert
            StepVerifier.create(result)
                    .assertNext(uuid -> {
                        assertThat(uuid).isEqualTo(UUID.fromString(USER_ID));
                    })
                    .verifyComplete();

            verify(usersResource).create(argThat(user -> {
                assertThat(user.getUsername()).isEqualTo(EMAIL);
                assertThat(user.getEmail()).isEqualTo(EMAIL);
                assertThat(user.getFirstName()).isEqualTo(FIRST_NAME);
                assertThat(user.getLastName()).isEqualTo(LAST_NAME);
                assertThat(user.getAttributes()).containsKey("phone");
                assertThat(user.getAttributes()).containsKey("user_type");
                assertThat(user.getAttributes()).containsKey("school_id");
                assertThat(user.getAttributes()).containsKey("school_name");
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle null school name")
        void shouldHandleNullSchoolName() {
            // Arrange
            CreateStaffRequest request = new CreateStaffRequest(
                    EMAIL,
                    PHONE,
                    FIRST_NAME,
                    LAST_NAME,
                    "TEACHER",
                    Set.of("TEACHER")
            );

            when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
            when(response.getStatus()).thenReturn(201);
            when(response.getHeaderString("Location"))
                    .thenReturn("http://localhost:8080/auth/realms/" + REALM + "/users/" + USER_ID);

            when(usersResource.get(USER_ID)).thenReturn(userResource);
            doNothing().when(userResource).resetPassword(any(CredentialRepresentation.class));

            // Act
            Mono<UUID> result = keycloakAdminService.createStaffUser(request, SCHOOL_ID, null);

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    // ========================================================================
    // ROLE MANAGEMENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Role Management Tests")
    class RoleManagementTests {

        @Test
        @DisplayName("Should assign realm roles to user")
        void shouldAssignRealmRolesToUser() {
            // Arrange
            Set<String> roleNames = Set.of("ADMIN", "ACCOUNTANT");

            when(realmResource.roles()).thenReturn(rolesResource);
            when(usersResource.get(USER_ID)).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            
            when(rolesResource.get("ADMIN")).thenReturn(roleResource);
            when(rolesResource.get("ACCOUNTANT")).thenReturn(roleResource);
            
            RoleRepresentation adminRole = new RoleRepresentation();
            adminRole.setName("ADMIN");
            RoleRepresentation accountantRole = new RoleRepresentation();
            accountantRole.setName("ACCOUNTANT");
            
            when(roleResource.toRepresentation())
                    .thenReturn(adminRole)
                    .thenReturn(accountantRole);

            // Mock the role scope resource for role assignment
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
            doNothing().when(roleScopeResource).add(anyList());

            // Act
            keycloakAdminService.assignRealmRoles(USER_ID, roleNames);

            // Assert
            verify(roleScopeResource).add(anyList());
        }

        @Test
        @DisplayName("Should remove realm roles from user")
        void shouldRemoveRealmRolesFromUser() {
            // Arrange
            Set<String> roleNames = Set.of("ADMIN");

            when(realmResource.roles()).thenReturn(rolesResource);
            when(usersResource.get(USER_ID)).thenReturn(userResource);
            when(rolesResource.get("ADMIN")).thenReturn(roleResource);
            
            RoleRepresentation adminRole = new RoleRepresentation();
            adminRole.setName("ADMIN");
            when(roleResource.toRepresentation()).thenReturn(adminRole);

            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
            doNothing().when(roleScopeResource).remove(anyList());

            // Act
            keycloakAdminService.removeRealmRoles(USER_ID, roleNames);

            // Assert
            verify(roleScopeResource).remove(anyList());
        }

        @Test
        @DisplayName("Should get user roles")
        void shouldGetUserRoles() {
            // Arrange
            List<RoleRepresentation> roleReps = List.of(
                    createRoleRepresentation("ADMIN"),
                    createRoleRepresentation("ACCOUNTANT")
            );

            when(usersResource.get(USER_ID)).thenReturn(userResource);
            when(userResource.roles()).thenReturn(roleMappingResource);
            when(roleMappingResource.realmLevel().listAll()).thenReturn(roleReps);

            // Act
            Set<String> roles = keycloakAdminService.getUserRoles(USER_ID);

            // Assert
            assertThat(roles).containsExactlyInAnyOrder("ADMIN", "ACCOUNTANT");
        }
    }

    // ========================================================================
    // PASSWORD MANAGEMENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("Password Management Tests")
    class PasswordManagementTests {

        @Test
        @DisplayName("Should set user password")
        void shouldSetUserPassword() {
            // Arrange
            when(usersResource.get(USER_ID)).thenReturn(userResource);
            doNothing().when(userResource).resetPassword(any(CredentialRepresentation.class));

            // Act
            keycloakAdminService.setUserPassword(USER_ID, "NewPass123!", false);

            // Assert
            verify(userResource).resetPassword(argThat(cred -> {
                assertThat(cred.getValue()).isEqualTo("NewPass123!");
                assertThat(cred.isTemporary()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Should send password reset email")
        void shouldSendPasswordResetEmail() {
            // Arrange
            when(usersResource.get(USER_ID)).thenReturn(userResource);
            doNothing().when(userResource).executeActionsEmail(anyList());

            // Act
            keycloakAdminService.sendPasswordResetEmail(USER_ID);

            // Assert
            verify(userResource).executeActionsEmail(List.of("UPDATE_PASSWORD"));
        }
    }

    // ========================================================================
    // USER MANAGEMENT TESTS
    // ========================================================================

    @Nested
    @DisplayName("User Management Tests")
    class UserManagementTests {

        @Test
        @DisplayName("Should find user by username")
        void shouldFindUserByUsername() {
            // Arrange
            UserRepresentation userRep = new UserRepresentation();
            userRep.setUsername(EMAIL);
            userRep.setEmail(EMAIL);

            when(usersResource.searchByUsername(EMAIL, true)).thenReturn(List.of(userRep));

            // Act
            Optional<UserRepresentation> result = keycloakAdminService.findByUsername(EMAIL);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getUsername()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("Should return empty optional when user not found by username")
        void shouldReturnEmptyOptionalWhenUserNotFoundByUsername() {
            // Arrange
            when(usersResource.searchByUsername(EMAIL, true)).thenReturn(Collections.emptyList());

            // Act
            Optional<UserRepresentation> result = keycloakAdminService.findByUsername(EMAIL);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should find user by email")
        void shouldFindUserByEmail() {
            // Arrange
            UserRepresentation userRep = new UserRepresentation();
            userRep.setEmail(EMAIL);

            when(usersResource.searchByEmail(EMAIL, true)).thenReturn(List.of(userRep));

            // Act
            Optional<UserRepresentation> result = keycloakAdminService.findByEmail(EMAIL);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo(EMAIL);
        }

        @Test
        @DisplayName("Should enable or disable user")
        void shouldEnableOrDisableUser() {
            // Arrange
            when(usersResource.get(USER_ID)).thenReturn(userResource);
            doNothing().when(userResource).update(any(UserRepresentation.class));

            // Act
            keycloakAdminService.setUserEnabled(USER_ID, false);

            // Assert
            verify(userResource).update(argThat(user -> {
                assertThat(user.isEnabled()).isFalse();
                return true;
            }));
        }

        @Test
        @DisplayName("Should delete user")
        void shouldDeleteUser() {
            // Arrange
            when(usersResource.get(USER_ID)).thenReturn(userResource);
            doNothing().when(userResource).remove();

            // Act
            keycloakAdminService.deleteUser(USER_ID);

            // Assert
            verify(userResource).remove();
        }

        @Test
        @DisplayName("Should update user attributes")
        void shouldUpdateUserAttributes() {
            // Arrange
            UserRepresentation existingUser = new UserRepresentation();
            existingUser.setAttributes(new HashMap<>());

            when(usersResource.get(USER_ID)).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(existingUser);
            doNothing().when(userResource).update(any(UserRepresentation.class));

            Map<String, List<String>> newAttributes = Map.of(
                    "phone", List.of("+2348098765432"),
                    "custom_attr", List.of("value")
            );

            // Act
            keycloakAdminService.updateUserAttributes(USER_ID, newAttributes);

            // Assert
            verify(userResource).update(argThat(user -> {
                assertThat(user.getAttributes()).containsKeys("phone", "custom_attr");
                return true;
            }));
        }

        @Test
        @DisplayName("Should handle null attributes when updating")
        void shouldHandleNullAttributesWhenUpdating() {
            // Arrange
            UserRepresentation existingUser = new UserRepresentation();
            existingUser.setAttributes(null);

            when(usersResource.get(USER_ID)).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(existingUser);
            doNothing().when(userResource).update(any(UserRepresentation.class));

            Map<String, List<String>> newAttributes = Map.of("phone", List.of(PHONE));

            // Act
            keycloakAdminService.updateUserAttributes(USER_ID, newAttributes);

            // Assert
            verify(userResource).update(argThat(user -> {
                assertThat(user.getAttributes()).isNotNull();
                assertThat(user.getAttributes()).containsKey("phone");
                return true;
            }));
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Helper method to create a RoleRepresentation with just a name.
     */
    private static RoleRepresentation createRoleRepresentation(String name) {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(name);
        return role;
    }
}
