package com.fee.app.schoolfeeapp.notification.service.impl;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationServiceImplIntegrationTest {

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
    private NotificationServiceImpl notificationService;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private KeycloakAdminServiceImpl keycloakAdminService;

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SMS_TEMPLATE_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID EMAIL_TEMPLATE_ID = UUID.fromString("e4e5f6a7-b890-1234-def1-234567890123");

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

    @Test
    @DisplayName("Should get templates filtered by normalized channel")
    void shouldGetTemplatesFilteredByNormalizedChannel() {
        seedSchool(SCHOOL_ID, "GIS");
        seedTemplate(SMS_TEMPLATE_ID, SCHOOL_ID, "FEE_REMINDER", "SMS", true, true);
        seedTemplate(EMAIL_TEMPLATE_ID, SCHOOL_ID, "FEE_REMINDER", "EMAIL", false, false);

        StepVerifier.create(notificationService.getTemplates(" sms "))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().templateId()).isEqualTo(SMS_TEMPLATE_ID);
                    assertThat(response.getFirst().channel()).isEqualTo("SMS");
                    assertThat(response.getFirst().variables()).containsExactly("parent_name", "amount");
                    assertThat(response.getFirst().isDefault()).isTrue();
                    assertThat(response.getFirst().isActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should update template and persist versioned lifecycle fields")
    void shouldUpdateTemplateAndPersistVersionedLifecycleFields() {
        seedSchool(SCHOOL_ID, "GIS");
        seedTemplate(SMS_TEMPLATE_ID, SCHOOL_ID, "FEE_REMINDER", "SMS", true, true);
        Integer initialVersion = fetchTemplateVersion(SMS_TEMPLATE_ID);

        StepVerifier.create(notificationService.updateTemplate(
                        SMS_TEMPLATE_ID,
                        new UpdateTemplateRequest(
                                "Updated body {amount}",
                                "Updated Fee Reminder",
                                false)))
                .assertNext(response -> {
                    assertThat(response.templateId()).isEqualTo(SMS_TEMPLATE_ID);
                    assertThat(response.updatedAt()).isNotNull();
                })
                .verifyComplete();

        Map<String, Object> row = fetchOne("""
                SELECT name, body_template, is_active, version
                FROM notification.notification_templates
                WHERE id = :templateId
                """, Map.of("templateId", SMS_TEMPLATE_ID));
        assertThat(row)
                .containsEntry("name", "Updated Fee Reminder")
                .containsEntry("body_template", "Updated body {amount}")
                .containsEntry("is_active", false);
        assertThat(((Number) row.get("version")).intValue()).isGreaterThan(initialVersion);
    }

    @Test
    @DisplayName("Should not update template from another school")
    void shouldNotUpdateTemplateFromAnotherSchool() {
        seedSchool(SCHOOL_ID, "GIS");
        seedSchool(OTHER_SCHOOL_ID, "OTH");
        seedTemplate(SMS_TEMPLATE_ID, OTHER_SCHOOL_ID, "FEE_REMINDER", "SMS", true, true);

        StepVerifier.create(notificationService.updateTemplate(
                        SMS_TEMPLATE_ID,
                        new UpdateTemplateRequest("Updated", null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TEMPLATE_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject unsupported channel")
    void shouldRejectUnsupportedChannel() {
        StepVerifier.create(notificationService.getTemplates("PUSH"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("UNSUPPORTED_CHANNEL");
                })
                .verify();
    }

    private void seedSchool(UUID schoolId, String code) {
        databaseClient.sql("""
                INSERT INTO school.schools (
                    id, name, code, email, phone, address, city, state, country,
                    payment_config, sms_config, term_config, is_active
                )
                VALUES (
                    :schoolId, :name, :code, :email, '+2348012345678', '12 School Road',
                    'Lagos', 'Lagos', 'Nigeria',
                    '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, true
                )
                """)
                .bind("schoolId", schoolId)
                .bind("name", "School " + code)
                .bind("code", code)
                .bind("email", code.toLowerCase() + "@school.test")
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedTemplate(
            UUID templateId, UUID schoolId, String code, String channel, boolean isDefault, boolean isActive) {
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
                .bind("schoolId", schoolId)
                .bind("code", code)
                .bind("name", channel + " Fee Reminder")
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

    private Integer fetchTemplateVersion(UUID templateId) {
        return ((Number) fetchOne("""
                SELECT version
                FROM notification.notification_templates
                WHERE id = :templateId
                """, Map.of("templateId", templateId)).get("version")).intValue();
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

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }
}
