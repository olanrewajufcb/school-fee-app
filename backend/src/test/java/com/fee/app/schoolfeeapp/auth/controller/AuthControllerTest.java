package com.fee.app.schoolfeeapp.auth.controller;

import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakConfigResponse;
import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import com.fee.app.schoolfeeapp.auth.service.AuthService;
import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID KEYCLOAK_ID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    // ========================================================================
    // GET CURRENT USER (/me) TESTS
    // ========================================================================

    @Nested
    @DisplayName("Get Current User Profile")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Should return school admin profile successfully")
        void shouldReturnSchoolAdminProfile() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("admin@school.edu")
                    .phoneNumber("+2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("SCHOOL_ADMIN")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("SCHOOL_ADMIN", "ACCOUNTANT"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now().minusHours(1))
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().getData()).isNotNull();
                        assertThat(responseEntity.getBody().getData().getUserId()).isEqualTo(USER_ID);
                        assertThat(responseEntity.getBody().getData().getKeycloakId()).isEqualTo(KEYCLOAK_ID);
                        assertThat(responseEntity.getBody().getData().getEmail()).isEqualTo("admin@school.edu");
                        assertThat(responseEntity.getBody().getData().getUserType()).isEqualTo("SCHOOL_ADMIN");
                        assertThat(responseEntity.getBody().getData().getSchoolId()).isEqualTo(SCHOOL_ID);
                        assertThat(responseEntity.getBody().getData().getSchoolName())
                                .isEqualTo("Grace International School");
                        assertThat(responseEntity.getBody().getData().getRoles())
                                .containsExactlyInAnyOrder("SCHOOL_ADMIN", "ACCOUNTANT");
                        assertThat(responseEntity.getBody().getData().getChildren()).isEmpty();
                        assertThat(responseEntity.getBody().getData().isActive()).isTrue();
                    })
                    .verifyComplete();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should return parent profile with children")
        void shouldReturnParentProfileWithChildren() {
            UserProfileResponse.ChildInfo child1 = UserProfileResponse.ChildInfo.builder()
                    .studentId(UUID.randomUUID())
                    .guardianId(UUID.randomUUID())
                    .relationship("MOTHER")
                    .canViewFees(true)
                    .canViewResults(true)
                    .canViewAttendance(true)
                    .build();

            UserProfileResponse.ChildInfo child2 = UserProfileResponse.ChildInfo.builder()
                    .studentId(UUID.randomUUID())
                    .guardianId(UUID.randomUUID())
                    .relationship("MOTHER")
                    .canViewFees(true)
                    .canViewResults(false)
                    .canViewAttendance(true)
                    .build();

            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("parent@email.com")
                    .phoneNumber("+2348098765432")
                    .firstName("Funke")
                    .lastName("Adebayo")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("PARENT"))
                    .children(List.of(child1, child2))
                    .lastLogin(ZonedDateTime.now().minusDays(1))
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().getUserType()).isEqualTo("PARENT");
                        assertThat(responseEntity.getBody().getData().getChildren()).hasSize(2);
                        assertThat(responseEntity.getBody().getData().getChildren().get(0).getRelationship())
                                .isEqualTo("MOTHER");
                        assertThat(responseEntity.getBody().getData().getChildren().get(0).isCanViewFees()).isTrue();
                        assertThat(responseEntity.getBody().getData().getChildren().get(0).isCanViewResults()).isTrue();
                        assertThat(responseEntity.getBody().getData().getChildren().get(1).isCanViewResults()).isFalse();
                    })
                    .verifyComplete();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should return teacher profile without children")
        void shouldReturnTeacherProfileWithoutChildren() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("teacher@school.edu")
                    .phoneNumber("+2348022222222")
                    .firstName("Jane")
                    .lastName("Smith")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now().minusMinutes(30))
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().getUserType()).isEqualTo("TEACHER");
                        assertThat(responseEntity.getBody().getData().getRoles()).containsOnly("TEACHER");
                        assertThat(responseEntity.getBody().getData().getChildren()).isEmpty();
                    })
                    .verifyComplete();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should return accountant profile")
        void shouldReturnAccountantProfile() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("accountant@school.edu")
                    .phoneNumber("+2348033333333")
                    .firstName("Accountant")
                    .lastName("User")
                    .userType("ACCOUNTANT")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("ACCOUNTANT"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now().minusHours(2))
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().getUserType()).isEqualTo("ACCOUNTANT");
                        assertThat(responseEntity.getBody().getData().getRoles()).containsOnly("ACCOUNTANT");
                    })
                    .verifyComplete();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should handle inactive user")
        void shouldHandleInactiveUser() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("inactive@school.edu")
                    .phoneNumber("+2348044444444")
                    .firstName("Inactive")
                    .lastName("User")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now().minusMonths(6))
                    .isActive(false)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().isActive()).isFalse();
                    })
                    .verifyComplete();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should handle user with null last login")
        void shouldHandleUserWithNullLastLogin() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("newuser@school.edu")
                    .phoneNumber("+2348055555555")
                    .firstName("New")
                    .lastName("User")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(null)
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().getLastLogin()).isNull();
                        assertThat(responseEntity.getBody().getData().isActive()).isTrue();
                    })
                    .verifyComplete();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should handle user with multiple roles")
        void shouldHandleUserWithMultipleRoles() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("multirole@school.edu")
                    .phoneNumber("+2348066666666")
                    .firstName("Multi")
                    .lastName("Role")
                    .userType("SCHOOL_ADMIN")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("SCHOOL_ADMIN", "ACCOUNTANT", "TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData().getRoles())
                                .containsExactlyInAnyOrder("SCHOOL_ADMIN", "ACCOUNTANT", "TEACHER");
                        assertThat(responseEntity.getBody().getData().getRoles()).hasSize(3);
                    })
                    .verifyComplete();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should handle authentication error")
        void shouldHandleAuthenticationError() {
            RuntimeException authError = new RuntimeException("Invalid JWT token");

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.error(authError));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should handle user not found error")
        void shouldHandleUserNotFoundError() {
            RuntimeException notFoundError = new RuntimeException("User not found");

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.error(notFoundError));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should handle database timeout error")
        void shouldHandleDatabaseTimeoutError() {
            RuntimeException timeoutError = new RuntimeException("Database query timed out");

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.error(timeoutError));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();

            verify(authService, times(1)).getCurrentUserProfile();
        }

        @Test
        @DisplayName("Should wrap response in ApiResponse structure")
        void shouldWrapResponseInApiResponseStructure() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("test@school.edu")
                    .phoneNumber("+2348077777777")
                    .firstName("Test")
                    .lastName("User")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getData()).isNotNull();
                        assertThat(responseEntity.getBody().getData().getEmail()).isEqualTo("test@school.edu");
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // KEYCLOAK CONFIG TESTS
    // ========================================================================

    @Nested
    @DisplayName("Get Keycloak Configuration")
    class GetKeycloakConfigTests {

        @Test
        @DisplayName("Should return Keycloak configuration successfully")
        void shouldReturnKeycloakConfigSuccessfully() {
            Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> result =
                    authController.getKeycloakConfig();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody()).isNotNull();
                        assertThat(responseEntity.getBody().isSuccess()).isTrue();
                        assertThat(responseEntity.getBody().getData()).isNotNull();

                        KeycloakConfigResponse config = responseEntity.getBody().getData();
                        assertThat(config.getAuthServerUrl()).isEqualTo("http://localhost:8081");
                        assertThat(config.getRealm()).isEqualTo("schoolfee");
                        assertThat(config.getClientId()).isEqualTo("schoolfee-web");
                        assertThat(config.getTokenEndpoint())
                                .isEqualTo("/realms/schoolfee/protocol/openid-connect/token");
                        assertThat(config.getLogoutEndpoint())
                                .isEqualTo("/realms/schoolfee/protocol/openid-connect/logout");
                    })
                    .verifyComplete();

            // No service call needed - config is static
            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Should return consistent configuration on multiple calls")
        void shouldReturnConsistentConfigurationOnMultipleCalls() {
            Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> result1 =
                    authController.getKeycloakConfig();

            Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> result2 =
                    authController.getKeycloakConfig();

            StepVerifier.create(result1)
                    .assertNext(response1 -> {
                        KeycloakConfigResponse config1 = response1.getBody().getData();

                        StepVerifier.create(result2)
                                .assertNext(response2 -> {
                                    KeycloakConfigResponse config2 = response2.getBody().getData();

                                    assertThat(config2.getAuthServerUrl())
                                            .isEqualTo(config1.getAuthServerUrl());
                                    assertThat(config2.getRealm())
                                            .isEqualTo(config1.getRealm());
                                    assertThat(config2.getClientId())
                                            .isEqualTo(config1.getClientId());
                                })
                                .verifyComplete();
                    })
                    .verifyComplete();

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Should not require authentication for config endpoint")
        void shouldNotRequireAuthenticationForConfigEndpoint() {
            // This test verifies that getKeycloakConfig doesn't depend on authService
            // which would require authentication
            Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> result =
                    authController.getKeycloakConfig();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(responseEntity.getBody().getData()).isNotNull();
                    })
                    .verifyComplete();

            verifyNoInteractions(authService);
        }

        @Test
        @DisplayName("Should return configuration with all required fields")
        void shouldReturnConfigurationWithAllRequiredFields() {
            Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> result =
                    authController.getKeycloakConfig();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        KeycloakConfigResponse config = responseEntity.getBody().getData();

                        // Verify all fields are present and non-null
                        assertThat(config.getAuthServerUrl()).isNotBlank();
                        assertThat(config.getRealm()).isNotBlank();
                        assertThat(config.getClientId()).isNotBlank();
                        assertThat(config.getTokenEndpoint()).isNotBlank();
                        assertThat(config.getLogoutEndpoint()).isNotBlank();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return configuration suitable for frontend integration")
        void shouldReturnConfigurationSuitableForFrontendIntegration() {
            Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> result =
                    authController.getKeycloakConfig();

            StepVerifier.create(result)
                    .assertNext(responseEntity -> {
                        KeycloakConfigResponse config = responseEntity.getBody().getData();

                        // Verify endpoints follow Keycloak conventions
                        assertThat(config.getTokenEndpoint())
                                .startsWith("/realms/")
                                .endsWith("/protocol/openid-connect/token");

                        assertThat(config.getLogoutEndpoint())
                                .startsWith("/realms/")
                                .endsWith("/protocol/openid-connect/logout");

                        // Verify realm name is lowercase (Keycloak convention)
                        assertThat(config.getRealm()).matches("^[a-z0-9-]+$");
                    })
                    .verifyComplete();
        }
    }

    // ========================================================================
    // RESPONSE STRUCTURE TESTS
    // ========================================================================

    @Nested
    @DisplayName("Response Structure Validation")
    class ResponseStructureTests {

        @Test
        @DisplayName("Should return 200 OK for successful /me request")
        void shouldReturn200OkForSuccessfulMeRequest() {
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("test@school.edu")
                    .phoneNumber("+2348011111111")
                    .firstName("Test")
                    .lastName("User")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity ->
                            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return 200 OK for successful keycloak-config request")
        void shouldReturn200OkForSuccessfulKeycloakConfigRequest() {
            Mono<ResponseEntity<ApiResponse<KeycloakConfigResponse>>> result =
                    authController.getKeycloakConfig();

            StepVerifier.create(result)
                    .assertNext(responseEntity ->
                            assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should include success flag in API response")
        void shouldIncludeSuccessFlagInApiResponse() {
            UserProfileResponse expectedProfile = createMinimalProfile();

            when(authService.getCurrentUserProfile())
                    .thenReturn(Mono.just(expectedProfile));

            Mono<ResponseEntity<ApiResponse<UserProfileResponse>>> result =
                    authController.getCurrentUser();

            StepVerifier.create(result)
                    .assertNext(responseEntity ->
                            assertThat(responseEntity.getBody().isSuccess()).isTrue())
                    .verifyComplete();
        }

        private UserProfileResponse createMinimalProfile() {
            return UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("minimal@school.edu")
                    .phoneNumber("+2348099999999")
                    .firstName("Minimal")
                    .lastName("Profile")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(ZonedDateTime.now())
                    .isActive(true)
                    .build();
        }
    }
}
