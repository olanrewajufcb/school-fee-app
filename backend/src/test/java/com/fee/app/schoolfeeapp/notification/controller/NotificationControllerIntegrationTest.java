package com.fee.app.schoolfeeapp.notification.controller;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class NotificationControllerIntegrationTest {

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

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SMS_TEMPLATE_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID EMAIL_TEMPLATE_ID = UUID.fromString("e4e5f6a7-b890-1234-def1-234567890123");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        cleanDatabase();
    }

    @Test
    @DisplayName("Should list templates through controller")
    void shouldListTemplatesThroughController() {
        seedSchool();
        seedTemplate(SMS_TEMPLATE_ID, "FEE_REMINDER", "SMS", true, true);
        seedTemplate(EMAIL_TEMPLATE_ID, "FEE_RECEIPT", "EMAIL", false, true);

        schoolAdminClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/notifications/templates")
                        .queryParam("channel", "sms")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.length()").isEqualTo(1)
                .jsonPath("$.data[0].templateId").isEqualTo(SMS_TEMPLATE_ID.toString())
                .jsonPath("$.data[0].channel").isEqualTo("SMS")
                .jsonPath("$.data[0].variables[0]").isEqualTo("parent_name")
                .jsonPath("$.data[0].isActive").isEqualTo(true);
    }

    @Test
    @DisplayName("Should update template through controller")
    void shouldUpdateTemplateThroughController() {
        seedSchool();
        seedTemplate(SMS_TEMPLATE_ID, "FEE_REMINDER", "SMS", true, true);

        schoolAdminClient()
                .put()
                .uri("/api/v1/notifications/templates/{templateId}", SMS_TEMPLATE_ID)
                .bodyValue(new UpdateTemplateRequest(
                        "Updated {amount}",
                        "Updated SMS Reminder",
                        false))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.templateId").isEqualTo(SMS_TEMPLATE_ID.toString())
                .jsonPath("$.data.updatedAt").exists();

        Map<String, Object> row = fetchOne("""
                SELECT name, body_template, is_active
                FROM notification.notification_templates
                WHERE id = :templateId
                """, Map.of("templateId", SMS_TEMPLATE_ID));
        assertThat(row)
                .containsEntry("name", "Updated SMS Reminder")
                .containsEntry("body_template", "Updated {amount}")
                .containsEntry("is_active", false);
    }

    @Test
    @DisplayName("Should reject unsupported channel through controller")
    void shouldRejectUnsupportedChannelThroughController() {
        seedSchool();

        schoolAdminClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/notifications/templates")
                        .queryParam("channel", "push")
                        .build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errors[0].code").isEqualTo("UNSUPPORTED_CHANNEL");
    }

    @Test
    @DisplayName("Should forbid accountant from updating templates")
    void shouldForbidAccountantFromUpdatingTemplates() {
        seedSchool();
        seedTemplate(SMS_TEMPLATE_ID, "FEE_REMINDER", "SMS", true, true);

        authenticatedClient("ACCOUNTANT", "ACCOUNTANT")
                .put()
                .uri("/api/v1/notifications/templates/{templateId}", SMS_TEMPLATE_ID)
                .bodyValue(new UpdateTemplateRequest("Updated", null, null))
                .exchange()
                .expectStatus().isForbidden();
    }

    private WebTestClient schoolAdminClient() {
        return authenticatedClient("SCHOOL_ADMIN", "SCHOOL_ADMIN");
    }

    private WebTestClient authenticatedClient(String userType, String... roles) {
        List<String> roleList = Arrays.asList(roles);
        return webTestClient.mutateWith(mockJwt()
                .jwt(jwt -> jwt
                        .subject(USER_ID.toString())
                        .claim("preferred_username", "admin")
                        .claim("email", "admin@gis.edu")
                        .claim("given_name", "Ada")
                        .claim("family_name", "Admin")
                        .claim("phone_number", "+2348012345678")
                        .claim("school_id", SCHOOL_ID.toString())
                        .claim("user_type", userType)
                        .claim("realm_access", Map.of("roles", roleList)))
                .authorities(roleList.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toArray(SimpleGrantedAuthority[]::new)));
    }

    private void seedSchool() {
        databaseClient.sql("""
                INSERT INTO school.schools (
                    id, name, code, email, phone, address, city, state, country,
                    payment_config, sms_config, term_config, is_active
                )
                VALUES (
                    :schoolId, 'Grace International School', 'GIS',
                    'hello@gis.edu', '+2348012345678', '12 School Road',
                    'Lagos', 'Lagos', 'Nigeria',
                    '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, true
                )
                """)
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedTemplate(
            UUID templateId, String code, String channel, boolean isDefault, boolean isActive) {
        databaseClient.sql("""
                INSERT INTO notification.notification_templates (
                    id, school_id, template_code, name, channel, body_template, variables,
                    is_default, is_active, created_at, updated_at, version
                )
                VALUES (
                    :templateId, :schoolId, :code, :name, :channel, :body, CAST(:variables AS jsonb),
                    :isDefault, :isActive, :createdAt, :updatedAt, 0
                )
                """)
                .bind("templateId", templateId)
                .bind("schoolId", SCHOOL_ID)
                .bind("code", code)
                .bind("name", channel + " Template")
                .bind("channel", channel)
                .bind("body", "Hello {parent_name}, pay {amount}.")
                .bind("variables", "[\"parent_name\",\"amount\"]")
                .bind("isDefault", isDefault)
                .bind("isActive", isActive)
                .bind("createdAt", Instant.parse("2026-06-05T10:00:00Z"))
                .bind("updatedAt", Instant.parse("2026-06-05T10:00:00Z"))
                .fetch()
                .rowsUpdated()
                .block();
    }

    private Map<String, Object> fetchOne(String sql, Map<String, ?> bindings) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, ?> binding : bindings.entrySet()) {
            spec = spec.bind(binding.getKey(), binding.getValue());
        }
        return spec.fetch().one().block();
    }

    private void cleanDatabase() {
        databaseClient.sql("DELETE FROM notification.notifications").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM notification.notification_templates").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }
}
