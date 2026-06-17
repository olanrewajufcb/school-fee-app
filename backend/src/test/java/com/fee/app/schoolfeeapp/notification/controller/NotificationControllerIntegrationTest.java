package com.fee.app.schoolfeeapp.notification.controller;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.notification.dto.request.SendBulkNotificationRequest;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import com.fee.app.schoolfeeapp.notification.service.SmsService;

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

    @MockitoBean
    private SmsService smsService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SMS_TEMPLATE_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID EMAIL_TEMPLATE_ID = UUID.fromString("e4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID CLASS_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_ID = UUID.fromString("a2222222-2222-2222-2222-222222222222");
    private static final UUID GUARDIAN_ID = UUID.fromString("a3333333-3333-3333-3333-333333333333");
    private static final UUID FEE_STRUCTURE_ID = UUID.fromString("a4444444-4444-4444-4444-444444444444");
    private static final UUID STUDENT_FEE_ID = UUID.fromString("a5555555-5555-5555-5555-555555555555");

    @BeforeEach
    void setUp() {
        reset(keycloakAdminService, reactiveJwtDecoder, smsService);
        when(reactiveJwtDecoder.decode(any()))
                .thenReturn(Mono.error(new JwtException("Invalid token")));
        when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());
        when(smsService.getBalance()).thenReturn(Mono.just(2500));
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
    @DisplayName("Should allow accountant to update templates")
    void shouldAllowAccountantToUpdateTemplates() {
        seedSchool();
        seedTemplate(SMS_TEMPLATE_ID, "FEE_REMINDER", "SMS", true, true);

        authenticatedClient("ACCOUNTANT", "ACCOUNTANT")
                .put()
                .uri("/api/v1/notifications/templates/{templateId}", SMS_TEMPLATE_ID)
                .bodyValue(new UpdateTemplateRequest("Updated", "Updated Name", true))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Should send bulk notifications through controller")
    void shouldSendBulkNotificationsThroughController() {
        seedBulkNotificationFixture();
        seedTemplate(SMS_TEMPLATE_ID, "FEE_REMINDER", "SMS", true, true);

        authenticatedClient("ACCOUNTANT", "ACCOUNTANT")
                .post()
                .uri("/api/v1/notifications/send-bulk")
                .bodyValue(new SendBulkNotificationRequest(
                        List.of(STUDENT_FEE_ID),
                        "FEE_REMINDER",
                        "SMS"))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.data.recipientsCount").isEqualTo(1)
                .jsonPath("$.data.estimatedCost").isEqualTo(4.0)
                .jsonPath("$.data.status").isEqualTo("QUEUED")
                .jsonPath("$.data.message").isEqualTo("1 of 1 messages queued for delivery");

        Map<String, Object> notification = fetchOne("""
                SELECT recipient_id, channel, template_code, status, context_id
                FROM notification.notifications
                WHERE school_id = :schoolId
                """, Map.of("schoolId", SCHOOL_ID));
        assertThat(notification)
                .containsEntry("recipient_id", GUARDIAN_ID)
                .containsEntry("channel", "SMS")
                .containsEntry("template_code", "FEE_REMINDER")
                .containsEntry("status", "SENT")
                .containsEntry("context_id", STUDENT_FEE_ID);
    }

    @Test
    @DisplayName("Should reject duplicate bulk notification targets through controller")
    void shouldRejectDuplicateBulkNotificationTargetsThroughController() {
        UUID feeId = UUID.randomUUID();

        schoolAdminClient()
                .post()
                .uri("/api/v1/notifications/send-bulk")
                .bodyValue(new SendBulkNotificationRequest(
                        List.of(feeId, feeId),
                        "FEE_REMINDER",
                        "SMS"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.errors[0].code").isEqualTo("DUPLICATE_NOTIFICATION_TARGET");
    }

    @Test
    @DisplayName("Should reject invalid bulk channel through controller validation")
    void shouldRejectInvalidBulkChannelThroughControllerValidation() {
        schoolAdminClient()
                .post()
                .uri("/api/v1/notifications/send-bulk")
                .bodyValue(Map.of(
                        "studentFeeIds", List.of(UUID.randomUUID().toString()),
                        "templateCode", "FEE_REMINDER",
                        "channel", "sms"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false);
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

    private void seedBulkNotificationFixture() {
        seedSchool();
        seedUser();
        seedClass();
        seedStudent();
        seedGuardian();
        seedGuardianLink();
        seedFeeStructure();
        seedStudentFee();
    }

    private void seedUser() {
        databaseClient.sql("""
                INSERT INTO auth.users (
                    id, keycloak_id, school_id, email, phone, first_name, last_name,
                    user_type, is_active
                )
                VALUES (
                    :userId, :keycloakId, :schoolId, 'admin@gis.edu', '+2348010000000',
                    'Ada', 'Admin', 'SCHOOL_ADMIN', true
                )
                """)
                .bind("userId", USER_ID)
                .bind("keycloakId", UUID.fromString("a6666666-6666-6666-6666-666666666666"))
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedClass() {
        databaseClient.sql("""
                INSERT INTO school.classes (
                    id, school_id, name, grade_level, section, capacity, is_active
                )
                VALUES (:classId, :schoolId, 'Primary 1A', 'PRIMARY_1', 'A', 40, true)
                """)
                .bind("classId", CLASS_ID)
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedStudent() {
        databaseClient.sql("""
                INSERT INTO school.students (
                    id, school_id, admission_number, first_name, last_name,
                    current_class_id, enrollment_date, enrollment_status
                )
                VALUES (
                    :studentId, :schoolId, 'ADM-001', 'Ada', 'Okafor',
                    :classId, :enrollmentDate, 'ACTIVE'
                )
                """)
                .bind("studentId", STUDENT_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("classId", CLASS_ID)
                .bind("enrollmentDate", LocalDate.parse("2026-01-15"))
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedGuardian() {
        databaseClient.sql("""
                INSERT INTO school.student_guardians (
                    id, school_id, first_name, last_name, phone, is_active
                )
                VALUES (:guardianId, :schoolId, 'Grace', 'Okafor', '+2348012345678', true)
                """)
                .bind("guardianId", GUARDIAN_ID)
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedGuardianLink() {
        databaseClient.sql("""
                INSERT INTO school.student_guardian_links (
                    id, guardian_id, student_id, school_id, relationship,
                    is_primary_contact, can_receive_sms, contact_priority
                )
                VALUES (
                    :linkId, :guardianId, :studentId, :schoolId, 'MOTHER',
                    true, true, 1
                )
                """)
                .bind("linkId", UUID.fromString("a7777777-7777-7777-7777-777777777777"))
                .bind("guardianId", GUARDIAN_ID)
                .bind("studentId", STUDENT_ID)
                .bind("schoolId", SCHOOL_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedFeeStructure() {
        databaseClient.sql("""
                INSERT INTO fee.fee_structures (
                    id, school_id, name, total_amount, due_date, status, created_by
                )
                VALUES (
                    :feeStructureId, :schoolId, 'Term 1 Fees', 5000.00,
                    :dueDate, 'ACTIVE', :createdBy
                )
                """)
                .bind("feeStructureId", FEE_STRUCTURE_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("dueDate", LocalDate.now().plusDays(14))
                .bind("createdBy", USER_ID)
                .fetch()
                .rowsUpdated()
                .block();
    }

    private void seedStudentFee() {
        databaseClient.sql("""
                INSERT INTO fee.student_fees (
                    id, fee_structure_id, student_id, school_id, total_amount,
                    discount_amount, due_date, is_late_fee_applied, late_fee_amount,
                    reminder_count
                )
                VALUES (
                    :studentFeeId, :feeStructureId, :studentId, :schoolId, 5000.00,
                    0.00, :dueDate, false, 0.00, 0
                )
                """)
                .bind("studentFeeId", STUDENT_FEE_ID)
                .bind("feeStructureId", FEE_STRUCTURE_ID)
                .bind("studentId", STUDENT_ID)
                .bind("schoolId", SCHOOL_ID)
                .bind("dueDate", LocalDate.now().plusDays(14))
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
        databaseClient.sql("DELETE FROM fee.student_fees").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM fee.fee_structures").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_guardian_links").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.student_guardians").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.students").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.classes").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM auth.users").fetch().rowsUpdated().block();
        databaseClient.sql("DELETE FROM school.schools").fetch().rowsUpdated().block();
    }
}
