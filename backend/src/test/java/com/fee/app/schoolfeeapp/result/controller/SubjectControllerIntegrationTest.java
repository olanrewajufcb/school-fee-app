package com.fee.app.schoolfeeapp.result.controller;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class SubjectControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("school_fee_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", () -> String.format(
                "jdbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private WebTestClient webTestClient;
    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;
    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID SCHOOL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
        seedSchool();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void shouldCreateListAndUpdateSubjectThroughController() {
        authenticatedClient("SCHOOL_ADMIN", SCHOOL_ID.toString(), "SCHOOL_ADMIN")
                .post()
                .uri("/api/v1/subjects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "  English   Language ",
                        "code", " eng ",
                        "category", " languages "))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.name").isEqualTo("English Language")
                .jsonPath("$.data.code").isEqualTo("ENG")
                .jsonPath("$.data.category").isEqualTo("LANGUAGES");

        String subjectId = extractSubjectId();

        authenticatedClient("SCHOOL_ADMIN", SCHOOL_ID.toString(), "SCHOOL_ADMIN")
                .get()
                .uri("/api/v1/subjects")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].name").isEqualTo("English Language");

        authenticatedClient("SCHOOL_ADMIN", SCHOOL_ID.toString(), "SCHOOL_ADMIN")
                .put()
                .uri("/api/v1/subjects/{subjectId}", subjectId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "name", "English Studies",
                        "code", " els ",
                        "category", "languages"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo("English Studies")
                .jsonPath("$.data.code").isEqualTo("ELS");
    }

    @Test
    void shouldReturnConflictForDuplicateSubject() {
        createSubject("Mathematics", "MTH");

        authenticatedClient("SCHOOL_ADMIN", SCHOOL_ID.toString(), "SCHOOL_ADMIN")
                .post()
                .uri("/api/v1/subjects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", " mathematics ", "code", "MATH"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_RESOURCE")
                .jsonPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void shouldValidateCreateRequestAndEnforceRoles() {
        authenticatedClient("SCHOOL_ADMIN", SCHOOL_ID.toString(), "SCHOOL_ADMIN")
                .post()
                .uri("/api/v1/subjects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "   "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors[0].field").isEqualTo("name");

        authenticatedClient("TEACHER", SCHOOL_ID.toString(), "TEACHER")
                .post()
                .uri("/api/v1/subjects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Biology"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void shouldLetSuperAdminManageSubjectsInSelectedSchoolContext() {
        authenticatedClient("SUPER_ADMIN", "*", "SUPER_ADMIN")
                .post()
                .uri("/api/v1/subjects")
                .header("X-School-ID", SCHOOL_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "Government", "code", "GOV"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.data.name").isEqualTo("Government");
    }

    private void createSubject(String name, String code) {
        authenticatedClient("SCHOOL_ADMIN", SCHOOL_ID.toString(), "SCHOOL_ADMIN")
                .post()
                .uri("/api/v1/subjects")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", name, "code", code))
                .exchange()
                .expectStatus().isCreated();
    }

    private String extractSubjectId() {
        return databaseClient.sql("SELECT id FROM result.subjects LIMIT 1")
                .map((row, metadata) -> row.get("id", UUID.class).toString())
                .one()
                .block();
    }

    private WebTestClient authenticatedClient(String userType, String schoolId, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID.toString())
                        .claim("preferred_username", "subject-admin")
                        .claim("email", "subject-admin@example.com")
                        .claim("given_name", "Subject")
                        .claim("family_name", "Admin")
                        .claim("school_id", schoolId)
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private void seedSchool() {
        databaseClient.sql("""
                        INSERT INTO school.schools (
                            id, name, code, email, phone, country,
                            payment_config, sms_config, term_config, is_active
                        )
                        VALUES (
                            :id, 'Controller Subject School', 'CSS', 'admin@css.edu',
                            '+2348022222222', 'Nigeria',
                            '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, true
                        )
                        """)
                .bind("id", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM result.class_subjects").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM result.subjects").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }
}
