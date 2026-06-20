package com.fee.app.schoolfeeapp.school.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.dto.request.ConfigureGradeLevelsRequest;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class GradeLevelControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);

        registry.add("spring.flyway.url", () ->
                String.format("jdbc:postgresql://%s:%d/%s",
                        postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Nested
    @DisplayName("GET /api/v1/schools/current/grade-levels")
    class GetSchoolGradeLevelsIntegrationTests {

        @Test
        @DisplayName("Should return all grade levels when school is unconfigured")
        void shouldReturnAllGradeLevelsWhenSchoolIsUnconfigured() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true, null);

            authenticatedClient(SCHOOL_ID, "TEACHER", "TEACHER")
                    .get()
                    .uri("/api/v1/schools/current/grade-levels")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(15)
                    .jsonPath("$.data[0].code").isEqualTo("NURSERY_1")
                    .jsonPath("$.data[14].code").isEqualTo("SSS_3");
        }

        @Test
        @DisplayName("Should return configured grade levels in canonical order")
        void shouldReturnConfiguredGradeLevelsInCanonicalOrder() {
            ObjectNode termConfig = objectMapper.createObjectNode();
            termConfig.putArray("gradeLevels")
                    .add("primary_2")
                    .add("BAD_CODE")
                    .add(" PRIMARY_1 ");
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true, termConfig);

            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .get()
                    .uri("/api/v1/schools/current/grade-levels")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(2)
                    .jsonPath("$.data[0].code").isEqualTo("PRIMARY_1")
                    .jsonPath("$.data[1].code").isEqualTo("PRIMARY_2");
        }

        @Test
        @DisplayName("Should return bad request for inactive school")
        void shouldReturnBadRequestForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false, null);

            authenticatedClient(SCHOOL_ID, "TEACHER", "TEACHER")
                    .get()
                    .uri("/api/v1/schools/current/grade-levels")
                    .exchange()
                    .expectStatus().isNotFound()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("SCHOOL_NOT_FOUND");
        }

        @Test
        @DisplayName("Should reject school grade levels without authentication")
        void shouldRejectSchoolGradeLevelsWithoutAuthentication() {
            webTestClient
                    .get()
                    .uri("/api/v1/schools/current/grade-levels")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject school grade levels for student role")
        void shouldRejectSchoolGradeLevelsForStudentRole() {
            authenticatedClient(SCHOOL_ID, "STUDENT", "STUDENT")
                    .get()
                    .uri("/api/v1/schools/current/grade-levels")
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    @Nested
    @DisplayName("GET /api/v1/schools/current/grade-levels/available")
    class GetAvailableGradeLevelsIntegrationTests {

        @Test
        @DisplayName("Should return available grade levels")
        void shouldReturnAvailableGradeLevels() {
            authenticatedClient(SCHOOL_ID, "TEACHER", "TEACHER")
                    .get()
                    .uri("/api/v1/schools/current/grade-levels/available")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.length()").isEqualTo(15)
                    .jsonPath("$.data[0].code").isEqualTo("NURSERY_1")
                    .jsonPath("$.data[14].code").isEqualTo("SSS_3");
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/schools/current/grade-levels")
    class ConfigureGradeLevelsIntegrationTests {

        @Test
        @DisplayName("Should configure grade levels for school admin")
        void shouldConfigureGradeLevelsForSchoolAdmin() {
            ObjectNode termConfig = objectMapper.createObjectNode();
            termConfig.put("termsPerYear", 3);
            ObjectNode namingConventions = objectMapper.createObjectNode();
            namingConventions.put("primary", "Primary {level}");
            namingConventions.put("seniorSecondary", "SSS {level}");
            termConfig.set("namingConventions", namingConventions);
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true, termConfig);

            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .put()
                    .uri("/api/v1/schools/current/grade-levels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ConfigureGradeLevelsRequest(
                            List.of(" primary_2 ", "PRIMARY_1"),
                            new ConfigureGradeLevelsRequest.NamingConvention(
                                    " Nursery {level} ",
                                    null,
                                    "JSS {level}",
                                    null)))
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(true)
                    .jsonPath("$.data.enabledLevels").isEqualTo(2)
                    .jsonPath("$.data.enabledLevelCodes[0]").isEqualTo("PRIMARY_1")
                    .jsonPath("$.data.enabledLevelCodes[1]").isEqualTo("PRIMARY_2");

            JsonNode savedConfig = readJsonNode(fetchOne("""
                    SELECT term_config::text AS term_config
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)).get("term_config"));
            assertThat(savedConfig.get("termsPerYear").asInt()).isEqualTo(3);
            assertThat(savedConfig.get("gradeLevels"))
                    .extracting(JsonNode::asText)
                    .containsExactly("PRIMARY_1", "PRIMARY_2");
            assertThat(savedConfig.get("namingConventions").get("nursery").asText()).isEqualTo("Nursery {level}");
            assertThat(savedConfig.get("namingConventions").get("primary").asText()).isEqualTo("Primary {level}");
            assertThat(savedConfig.get("namingConventions").get("juniorSecondary").asText()).isEqualTo("JSS {level}");
            assertThat(savedConfig.get("namingConventions").get("seniorSecondary").asText()).isEqualTo("SSS {level}");
        }

        @Test
        @DisplayName("Should return bad request for invalid grade level codes")
        void shouldReturnBadRequestForInvalidGradeLevelCodes() {
            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .put()
                    .uri("/api/v1/schools/current/grade-levels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ConfigureGradeLevelsRequest(
                            List.of("PRIMARY_1", "BAD_CODE"),
                            null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("INVALID_GRADE_LEVELS")
                    .jsonPath("$.errors[0].field").isEqualTo("enabledLevels");
        }

        @Test
        @DisplayName("Should return bad request for duplicate grade level codes")
        void shouldReturnBadRequestForDuplicateGradeLevelCodes() {
            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .put()
                    .uri("/api/v1/schools/current/grade-levels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ConfigureGradeLevelsRequest(
                            List.of("PRIMARY_1", "primary_1"),
                            null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_GRADE_LEVELS")
                    .jsonPath("$.errors[0].field").isEqualTo("enabledLevels");
        }

        @Test
        @DisplayName("Should return validation error for empty grade levels")
        void shouldReturnValidationErrorForEmptyGradeLevels() {
            authenticatedClient(SCHOOL_ID, "SCHOOL_ADMIN", "SCHOOL_ADMIN")
                    .put()
                    .uri("/api/v1/schools/current/grade-levels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ConfigureGradeLevelsRequest(List.of(), null))
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.success").isEqualTo(false)
                    .jsonPath("$.errors[0].code").isEqualTo("VALIDATION_ERROR")
                    .jsonPath("$.errors[0].field").isEqualTo("enabledLevels");
        }

        @Test
        @DisplayName("Should reject configure grade levels without authentication")
        void shouldRejectConfigureGradeLevelsWithoutAuthentication() {
            webTestClient
                    .put()
                    .uri("/api/v1/schools/current/grade-levels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ConfigureGradeLevelsRequest(List.of("PRIMARY_1"), null))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        @DisplayName("Should reject configure grade levels for accountant role")
        void shouldRejectConfigureGradeLevelsForAccountantRole() {
            authenticatedClient(SCHOOL_ID, "ACCOUNTANT", "ACCOUNTANT")
                    .put()
                    .uri("/api/v1/schools/current/grade-levels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new ConfigureGradeLevelsRequest(List.of("PRIMARY_1"), null))
                    .exchange()
                    .expectStatus().isForbidden();
        }
    }

    private WebTestClient authenticatedClient(UUID schoolId, String userType, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID.toString())
                        .claim("preferred_username", "testuser")
                        .claim("email", "test@school.edu")
                        .claim("given_name", "Test")
                        .claim("family_name", "User")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", schoolId != null ? schoolId.toString() : "*")
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private School seedSchool(
            UUID schoolId,
            String schoolName,
            String schoolCode,
            boolean active,
            JsonNode termConfig) {
        School school = School.builder()
                .id(schoolId)
                .name(schoolName)
                .code(schoolCode)
                .email("hello-" + schoolCode.toLowerCase() + "@gis.edu")
                .phone("+2348012345678")
                .address("12 School Road")
                .city("Lagos")
                .state("Lagos")
                .country("Nigeria")
                .logoUrl("https://cdn.example.com/logo.png")
                .paymentConfig(objectMapper.createObjectNode())
                .smsConfig(objectMapper.createObjectNode())
                .termConfig(termConfig)
                .isActive(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return schoolRepository.save(school).block();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM outbox.outbox_events").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notifications").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notification_templates").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_structure_classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_categories").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_class_history").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.students").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.terms").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.academic_sessions").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }

    private Map<String, Object> fetchOne(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return spec.fetch().one().block();
    }

    private JsonNode readJsonNode(Object value) {
        try {
            return objectMapper.readTree(value.toString());
        } catch (Exception error) {
            throw new AssertionError("Failed to parse JSON value: " + value, error);
        }
    }
}
