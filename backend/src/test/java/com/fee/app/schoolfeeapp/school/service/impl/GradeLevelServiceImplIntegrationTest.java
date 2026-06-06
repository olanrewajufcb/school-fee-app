package com.fee.app.schoolfeeapp.school.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.school.domain.School;
import com.fee.app.schoolfeeapp.school.dto.request.ConfigureGradeLevelsRequest;
import com.fee.app.schoolfeeapp.school.repository.SchoolRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GradeLevelServiceImplIntegrationTest {

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
    private GradeLevelServiceImpl gradeLevelService;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID USER_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(jwtUtils, keycloakAdminService, reactiveJwtDecoder);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Nested
    @DisplayName("Grade Level Service Integration Tests")
    class GradeLevelIntegrationTests {

        @Test
        @DisplayName("Should return all available grade levels")
        void shouldReturnAllAvailableGradeLevels() {
            StepVerifier.create(gradeLevelService.getAvailableGradeLevels())
                    .assertNext(response -> {
                        assertThat(response).hasSize(15);
                        assertThat(response.get(0).code()).isEqualTo("NURSERY_1");
                        assertThat(response.get(14).code()).isEqualTo("SSS_3");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return all school grade levels when unconfigured")
        void shouldReturnAllSchoolGradeLevelsWhenUnconfigured() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true, null);

            StepVerifier.create(gradeLevelService.getSchoolGradeLevels())
                    .assertNext(response -> {
                        assertThat(response).hasSize(15);
                        assertThat(response.get(0).code()).isEqualTo("NURSERY_1");
                        assertThat(response.get(14).code()).isEqualTo("SSS_3");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should return configured school grade levels in canonical order")
        void shouldReturnConfiguredSchoolGradeLevelsInCanonicalOrder() {
            ObjectNode termConfig = objectMapper.createObjectNode();
            termConfig.putArray("gradeLevels")
                    .add("primary_2")
                    .add("BAD_CODE")
                    .add(" PRIMARY_1 ");
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true, termConfig);

            StepVerifier.create(gradeLevelService.getSchoolGradeLevels())
                    .assertNext(response -> {
                        assertThat(response).hasSize(2);
                        assertThat(response.get(0).code()).isEqualTo("PRIMARY_1");
                        assertThat(response.get(1).code()).isEqualTo("PRIMARY_2");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Should configure grade levels and preserve existing term config")
        void shouldConfigureGradeLevelsAndPreserveExistingTermConfig() {
            ObjectNode termConfig = objectMapper.createObjectNode();
            termConfig.put("termsPerYear", 3);
            ObjectNode namingConventions = objectMapper.createObjectNode();
            namingConventions.put("primary", "Primary {level}");
            namingConventions.put("seniorSecondary", "SSS {level}");
            termConfig.set("namingConventions", namingConventions);
            School school = seedSchool(SCHOOL_ID, "Grace International School", "GIS", true, termConfig);

            StepVerifier.create(gradeLevelService.configureGradeLevels(new ConfigureGradeLevelsRequest(
                            List.of(" primary_2 ", "PRIMARY_1"),
                            new ConfigureGradeLevelsRequest.NamingConvention(
                                    " Nursery {level} ",
                                    null,
                                    "JSS {level}",
                                    null))))
                    .assertNext(response -> {
                        assertThat(response.enabledLevels()).isEqualTo(2);
                        assertThat(response.enabledLevelCodes()).containsExactly("PRIMARY_1", "PRIMARY_2");
                    })
                    .verifyComplete();

            Map<String, Object> updatedSchool = fetchOne("""
                    SELECT term_config::text AS term_config, updated_at, version
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID));
            JsonNode savedConfig = readJsonNode(updatedSchool.get("term_config"));
            assertThat(savedConfig.get("termsPerYear").asInt()).isEqualTo(3);
            assertThat(savedConfig.get("gradeLevels"))
                    .extracting(JsonNode::asText)
                    .containsExactly("PRIMARY_1", "PRIMARY_2");
            assertThat(savedConfig.get("namingConventions").get("nursery").asText()).isEqualTo("Nursery {level}");
            assertThat(savedConfig.get("namingConventions").get("primary").asText()).isEqualTo("Primary {level}");
            assertThat(savedConfig.get("namingConventions").get("juniorSecondary").asText()).isEqualTo("JSS {level}");
            assertThat(savedConfig.get("namingConventions").get("seniorSecondary").asText()).isEqualTo("SSS {level}");
            assertThat(updatedSchool.get("updated_at")).isNotNull();
            assertThat(((Number) updatedSchool.get("version")).intValue()).isGreaterThan(school.getVersion());
        }

        @Test
        @DisplayName("Should reject invalid grade level codes")
        void shouldRejectInvalidGradeLevelCodes() {
            StepVerifier.create(gradeLevelService.configureGradeLevels(new ConfigureGradeLevelsRequest(
                            List.of("PRIMARY_1", "BAD_CODE"),
                            null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("INVALID_GRADE_LEVELS");
                        assertThat(exception.getField()).isEqualTo("enabledLevels");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject duplicate grade level codes")
        void shouldRejectDuplicateGradeLevelCodes() {
            StepVerifier.create(gradeLevelService.configureGradeLevels(new ConfigureGradeLevelsRequest(
                            List.of("PRIMARY_1", "primary_1"),
                            null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("DUPLICATE_GRADE_LEVELS");
                        assertThat(exception.getField()).isEqualTo("enabledLevels");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should reject configure grade levels for inactive school")
        void shouldRejectConfigureGradeLevelsForInactiveSchool() {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", false, null);

            StepVerifier.create(gradeLevelService.configureGradeLevels(new ConfigureGradeLevelsRequest(
                            List.of("PRIMARY_1"),
                            null)))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SchoolFeeException.class);
                        SchoolFeeException exception = (SchoolFeeException) error;
                        assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
                    })
                    .verify();
        }

        @Test
        @DisplayName("Should not configure grade levels after concurrent school deactivation commits")
        void shouldNotConfigureGradeLevelsAfterConcurrentSchoolDeactivationCommits() throws Exception {
            seedSchool(SCHOOL_ID, "Grace International School", "GIS", true, null);

            CompletableFuture<Throwable> configureFuture = null;
            try (Connection lockConnection = DriverManager.getConnection(
                    postgres.getJdbcUrl(),
                    postgres.getUsername(),
                    postgres.getPassword())) {
                lockConnection.setAutoCommit(false);
                lockActiveSchoolRow(lockConnection, SCHOOL_ID);

                configureFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        gradeLevelService.configureGradeLevels(new ConfigureGradeLevelsRequest(
                                        List.of("PRIMARY_1"),
                                        null))
                                .block(Duration.ofSeconds(5));
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                });

                Thread.sleep(300);
                assertThat(configureFuture).isNotDone();

                deactivateLockedSchool(lockConnection, SCHOOL_ID);
                lockConnection.commit();

                Throwable error = configureFuture.get(5, TimeUnit.SECONDS);
                assertThat(error).isInstanceOf(SchoolFeeException.class);
                SchoolFeeException exception = (SchoolFeeException) error;
                assertThat(exception.getErrorCode()).isEqualTo("SCHOOL_NOT_FOUND");
            } finally {
                if (configureFuture != null && !configureFuture.isDone()) {
                    configureFuture.cancel(true);
                }
            }

            assertThat(fetchOne("""
                    SELECT term_config
                    FROM school.schools
                    WHERE id = :schoolId
                    """, Map.of("schoolId", SCHOOL_ID)))
                    .containsEntry("term_config", null);
        }
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

    private void lockActiveSchoolRow(Connection connection, UUID schoolId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM school.schools
                WHERE id = ? AND is_active = true
                FOR UPDATE
                """)) {
            statement.setObject(1, schoolId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
            }
        }
    }

    private void deactivateLockedSchool(Connection connection, UUID schoolId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE school.schools
                SET is_active = false
                WHERE id = ?
                """)) {
            statement.setObject(1, schoolId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .email("admin@gis.edu")
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }
}
