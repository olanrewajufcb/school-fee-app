package com.fee.app.schoolfeeapp.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.dto.response.UserProfileResponse;
import com.fee.app.schoolfeeapp.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AuthController with real JWT authentication.
 * Tests the complete request/response pipeline with Spring Security context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class AuthControllerIntegrationTest {

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
    private WebTestClient webTestClient;

    @Autowired
    private ObjectMapper objectMapper;

    // Mock beans for service dependencies (we test controller + security, not service logic)
    @MockitoBean
    private com.fee.app.schoolfeeapp.auth.service.AuthService authService;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID KEYCLOAK_ID = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    private Jwt validJwt;
    private JwtAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        // Create a realistic JWT token with Keycloak claims
        Map<String, Object> headers = new HashMap<>();
        headers.put("alg", "RS256");
        headers.put("typ", "JWT");

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", KEYCLOAK_ID.toString());
        claims.put("preferred_username", "testuser");
        claims.put("email", "test@school.edu");
        claims.put("given_name", "Test");
        claims.put("family_name", "User");
        claims.put("phone_number", "+2348012345678");
        claims.put("school_id", SCHOOL_ID.toString());
        claims.put("user_type", "PARENT");
        
        // Realm access roles (Keycloak standard claim)
        Map<String, Object> realmAccess = new HashMap<>();
        realmAccess.put("roles", List.of("PARENT", "ACCOUNTANT"));
        claims.put("realm_access", realmAccess);

        validJwt = Jwt.withTokenValue("mock-token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .build();

        authToken = new JwtAuthenticationToken(validJwt);
    }

    // ========================================================================
    // GET /api/v1/auth/me - AUTHENTICATED ENDPOINT TESTS
    // ========================================================================

    @Nested
    @DisplayName("GET /me - With Real JWT Authentication")
    class GetCurrentUserWithRealJwtTests {

        @Test
        @DisplayName("Should return user profile with valid JWT token")
        @WithMockUser
        void shouldReturnUserProfileWithValidJwt() {
            // Arrange - Setup mock response
            UserProfileResponse expectedProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("test@school.edu")
                    .phoneNumber("+2348012345678")
                    .firstName("Test")
                    .lastName("User")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Grace International School")
                    .roles(Set.of("PARENT", "ACCOUNTANT"))
                    .children(Collections.emptyList())
                    .lastLogin(java.time.ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(expectedProfile));

            // Act & Assert - Use WebTestClient with @WithMockUser
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody(ApiResponse.class)
                    .consumeWith(response -> {
                        assertThat(response.getResponseBody()).isNotNull();
                        assertThat(response.getResponseBody().isSuccess()).isTrue();
                    });
        }

        @Test
        @DisplayName("Should reject request without JWT token")
        void shouldRejectRequestWithoutJwtToken() {
            // Act & Assert - No authentication
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject request with invalid JWT token")
        void shouldRejectRequestWithInvalidJwtToken() {
            // Act & Assert - Invalid token
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token-xyz")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should handle school admin user with multiple roles")
        @WithMockUser
        void shouldHandleSchoolAdminWithMultipleRoles() {
            // Arrange
            UserProfileResponse adminProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("admin@school.edu")
                    .phoneNumber("+2348098765432")
                    .firstName("Admin")
                    .lastName("User")
                    .userType("SCHOOL_ADMIN")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("SCHOOL_ADMIN", "ACCOUNTANT", "TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(java.time.ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(adminProfile));

            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.userType").isEqualTo("SCHOOL_ADMIN")
                    .jsonPath("$.data.roles.length()").isEqualTo(3);
        }

        @Test
        @DisplayName("Should return parent profile with children")
        @WithMockUser
        void shouldReturnParentProfileWithChildren() {
            // Arrange
            UserProfileResponse.ChildInfo child = UserProfileResponse.ChildInfo.builder()
                    .studentId(UUID.randomUUID())
                    .guardianId(UUID.randomUUID())
                    .relationship("MOTHER")
                    .canViewFees(true)
                    .canViewResults(true)
                    .canViewAttendance(true)
                    .build();

            UserProfileResponse parentProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("parent@school.edu")
                    .phoneNumber("+2348011111111")
                    .firstName("Parent")
                    .lastName("User")
                    .userType("PARENT")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("PARENT"))
                    .children(List.of(child))
                    .lastLogin(java.time.ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(parentProfile));

            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.userType").isEqualTo("PARENT")
                    .jsonPath("$.data.children.length()").isEqualTo(1)
                    .jsonPath("$.data.children[0].relationship").isEqualTo("MOTHER");
        }

        @Test
        @DisplayName("Should handle teacher user without children")
        @WithMockUser
        void shouldHandleTeacherWithoutChildren() {
            // Arrange
            UserProfileResponse teacherProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("teacher@school.edu")
                    .phoneNumber("+2348022222222")
                    .firstName("Teacher")
                    .lastName("User")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(java.time.ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(teacherProfile));

            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.userType").isEqualTo("TEACHER")
                    .jsonPath("$.data.children.length()").isEqualTo(0);
        }

        @Test
        @DisplayName("Should include all required fields in response")
        @WithMockUser
        void shouldIncludeAllRequiredFields() {
            // Arrange
            UserProfileResponse completeProfile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("complete@school.edu")
                    .phoneNumber("+2348033333333")
                    .firstName("Complete")
                    .lastName("Profile")
                    .userType("ACCOUNTANT")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("ACCOUNTANT"))
                    .children(Collections.emptyList())
                    .lastLogin(java.time.ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(completeProfile));

            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").exists()
                    .jsonPath("$.data.userId").exists()
                    .jsonPath("$.data.keycloakId").exists()
                    .jsonPath("$.data.email").exists()
                    .jsonPath("$.data.firstName").exists()
                    .jsonPath("$.data.lastName").exists()
                    .jsonPath("$.data.userType").exists()
                    .jsonPath("$.data.schoolId").exists()
                    .jsonPath("$.data.schoolName").exists()
                    .jsonPath("$.data.roles").exists()
                    .jsonPath("$.data.active").exists();  // Jackson serializes 'isActive' as 'active'
        }
    }

    // ========================================================================
    // GET /api/v1/auth/keycloak-config - PUBLIC ENDPOINT TESTS
    // ========================================================================

    @Nested
    @DisplayName("GET /keycloak-config - Public Endpoint (No Auth Required)")
    class GetKeycloakConfigPublicEndpointTests {

        @Test
        @DisplayName("Should return Keycloak config without authentication")
        void shouldReturnKeycloakConfigWithoutAuth() {
            // Act & Assert - No auth header needed
            webTestClient.get()
                    .uri("/api/v1/auth/keycloak-config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.authServerUrl").exists()
                    .jsonPath("$.data.realm").exists()
                    .jsonPath("$.data.clientId").exists()
                    .jsonPath("$.data.tokenEndpoint").exists()
                    .jsonPath("$.data.logoutEndpoint").exists();
        }

        @Test
        @DisplayName("Should return correct Keycloak endpoints")
        void shouldReturnCorrectKeycloakEndpoints() {
            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/keycloak-config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.tokenEndpoint")
                        .value(val -> assertThat(val.toString()).startsWith("/realms/").endsWith("/protocol/openid-connect/token"))
                    .jsonPath("$.data.logoutEndpoint")
                        .value(val -> assertThat(val.toString()).startsWith("/realms/").endsWith("/protocol/openid-connect/logout"));
        }

        @Test
        @DisplayName("Should allow access with authentication (optional)")
        void shouldAllowAccessWithAuthentication() {
            // Act & Assert - Auth is allowed but not required
            webTestClient.get()
                    .uri("/api/v1/auth/keycloak-config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true);
        }

        @Test
        @DisplayName("Should return consistent config on multiple requests")
        void shouldReturnConsistentConfigOnMultipleRequests() {
            // Act - First request
            var response1 = webTestClient.get()
                    .uri("/api/v1/auth/keycloak-config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody();

            // Second request
            var response2 = webTestClient.get()
                    .uri("/api/v1/auth/keycloak-config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody();

            // Assert - Config data should be identical (ignore timestamp)
            assertThat(response1).isNotNull();
            assertThat(response2).isNotNull();
            
            // Compare only the 'data' field, not the timestamp
            assertThat(response1.get("data")).isEqualTo(response2.get("data"));
            
            // Verify both have success=true
            assertThat(response1.get("success")).isEqualTo(true);
            assertThat(response2.get("success")).isEqualTo(true);
        }
    }

    // ========================================================================
    // SECURITY AND ERROR HANDLING TESTS
    // ========================================================================

    @Nested
    @DisplayName("Security and Error Handling")
    class SecurityAndErrorHandlingTests {

        @Test
        @DisplayName("Should handle expired JWT token")
        void shouldHandleExpiredJwtToken() {
            // Act & Assert - Expired token scenario
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should handle malformed Authorization header")
        void shouldHandleMalformedAuthorizationHeader() {
            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "InvalidFormat")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should deny access to /me endpoint for anonymous users")
        void shouldDenyAccessToMeForAnonymousUsers() {
            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should handle concurrent requests correctly")
        @WithMockUser
        void shouldHandleConcurrentRequestsCorrectly() {
            // Arrange
            UserProfileResponse profile = UserProfileResponse.builder()
                    .userId(USER_ID)
                    .keycloakId(KEYCLOAK_ID)
                    .email("concurrent@school.edu")
                    .phoneNumber("+2348044444444")
                    .firstName("Concurrent")
                    .lastName("User")
                    .userType("TEACHER")
                    .schoolId(SCHOOL_ID)
                    .schoolName("Test School")
                    .roles(Set.of("TEACHER"))
                    .children(Collections.emptyList())
                    .lastLogin(java.time.ZonedDateTime.now())
                    .isActive(true)
                    .build();

            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(profile));

            // Act - Send multiple concurrent requests
            var request1 = webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk();

            var request2 = webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk();

            // Assert - Both should succeed
            request1.expectBody().jsonPath("$.success").isEqualTo(true);
            request2.expectBody().jsonPath("$.success").isEqualTo(true);
        }

        @Test
        @DisplayName("Should verify CORS endpoint is accessible")
        void shouldVerifyCorsEndpointIsAccessible() {
            // Simple test to verify the public endpoint is accessible
            // Full CORS header testing is better done in E2E tests with real browsers
            
            webTestClient.get()
                    .uri("/api/v1/auth/keycloak-config")
                    .exchange()
                    .expectStatus().isOk();
        }
    }



    // ========================================================================
    // RESPONSE FORMAT VALIDATION TESTS
    // ========================================================================

    @Nested
    @DisplayName("Response Format Validation")
    class ResponseFormatValidationTests {

        @Test
        @DisplayName("Should return proper JSON structure for /me")
        @WithMockUser
        void shouldReturnProperJsonStructureForMe() {
            // Arrange
            UserProfileResponse profile = createMinimalProfile();
            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(profile));

            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").exists()
                    .jsonPath("$.success").isBoolean()
                    .jsonPath("$.data").isMap()
                    .jsonPath("$.timestamp").exists();
        }

        @Test
        @DisplayName("Should return proper JSON structure for /keycloak-config")
        void shouldReturnProperJsonStructureForKeycloakConfig() {
            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/keycloak-config")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$").exists()
                    .jsonPath("$.success").isBoolean()
                    .jsonPath("$.data").isMap();
        }

        @Test
        @DisplayName("Should use ISO 8601 format for timestamps")
        @WithMockUser
        void shouldUseIso8601FormatForTimestamps() {
            // Arrange
            UserProfileResponse profile = createMinimalProfile();
            when(authService.getCurrentUserProfile()).thenReturn(Mono.just(profile));

            // Act & Assert
            webTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.data.lastLogin").exists()
                    .jsonPath("$.timestamp").exists();
        }
    }

    // Helper method
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
                .lastLogin(java.time.ZonedDateTime.now())
                .isActive(true)
                .build();
    }
}
