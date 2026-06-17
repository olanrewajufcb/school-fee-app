package com.fee.app.schoolfeeapp.notification.service.impl;

import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.notification.dto.request.SendBulkNotificationRequest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import com.fee.app.schoolfeeapp.notification.service.SmsService;

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

    @MockitoBean
    private SmsService smsService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID OTHER_SCHOOL_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID SMS_TEMPLATE_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID EMAIL_TEMPLATE_ID = UUID.fromString("e4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID REMINDER_SCHEDULE_ID = UUID.fromString("f4e5f6a7-b890-1234-def1-234567890123");
    private static final UUID CLASS_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");
    private static final UUID STUDENT_ID = UUID.fromString("a2222222-2222-2222-2222-222222222222");
    private static final UUID GUARDIAN_ID = UUID.fromString("a3333333-3333-3333-3333-333333333333");
    private static final UUID FEE_STRUCTURE_ID = UUID.fromString("a4444444-4444-4444-4444-444444444444");
    private static final UUID STUDENT_FEE_ID = UUID.fromString("a5555555-5555-5555-5555-555555555555");

    @BeforeEach
    void setUp() {
        cleanDatabase();
        reset(jwtUtils, keycloakAdminService, reactiveJwtDecoder, smsService);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(smsService.send(anyString(), anyString())).thenReturn(Mono.empty());
        when(smsService.getBalance()).thenReturn(Mono.just(2500));
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
    @DisplayName("Should get reminder schedules for current school from migrated table")
    void shouldGetReminderSchedulesForCurrentSchool() {
        seedSchool(SCHOOL_ID, "GIS");
        seedSchool(OTHER_SCHOOL_ID, "OTH");
        seedReminderSchedule(REMINDER_SCHEDULE_ID, SCHOOL_ID, "First reminder", true);
        seedReminderSchedule(UUID.fromString("f5e5f6a7-b890-1234-def1-234567890123"), OTHER_SCHOOL_ID, "Other reminder", true);

        StepVerifier.create(notificationService.getReminderSchedules())
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().scheduleId()).isEqualTo(REMINDER_SCHEDULE_ID);
                    assertThat(response.getFirst().name()).isEqualTo("First reminder");
                    assertThat(response.getFirst().triggerType()).isEqualTo("BEFORE_DUE_DATE");
                    assertThat(response.getFirst().daysOffset()).isEqualTo(7);
                    assertThat(response.getFirst().sendTime()).isEqualTo(LocalTime.of(9, 30));
                    assertThat(response.getFirst().templateCode()).isEqualTo("FEE_REMINDER");
                    assertThat(response.getFirst().isActive()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty reminder schedules for a new school")
    void shouldReturnEmptyReminderSchedulesForNewSchool() {
        seedSchool(SCHOOL_ID, "GIS");

        StepVerifier.create(notificationService.getReminderSchedules())
                .assertNext(response -> assertThat(response).isEmpty())
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

    @Test
    @DisplayName("Should send bulk notification and persist delivery log")
    void shouldSendBulkNotificationAndPersistDeliveryLog() {
        seedBulkNotificationFixture();
        seedTemplate(SMS_TEMPLATE_ID, SCHOOL_ID, "FEE_REMINDER", "SMS", true, true);

        StepVerifier.create(notificationService.sendBulkNotifications(
                        new SendBulkNotificationRequest(
                                java.util.List.of(STUDENT_FEE_ID),
                                "FEE_REMINDER",
                                " sms ")))
                .assertNext(response -> {
                    assertThat(response.recipientsCount()).isEqualTo(1);
                    assertThat(response.estimatedCost()).isEqualByComparingTo(BigDecimal.valueOf(4));
                    assertThat(response.status()).isEqualTo("QUEUED");
                    assertThat(response.message()).isEqualTo("1 of 1 messages queued for delivery");
                })
                .verifyComplete();

        Map<String, Object> notification = fetchOne("""
                SELECT recipient_id, recipient_phone, channel, template_code, status,
                       provider_message_id, provider_cost, correlation_id, context_type,
                       context_id, idempotency_key, rendered_body
                FROM notification.notifications
                WHERE school_id = :schoolId
                """, Map.of("schoolId", SCHOOL_ID));
        assertThat(notification)
                .containsEntry("recipient_id", GUARDIAN_ID)
                .containsEntry("recipient_phone", "+2348012345678")
                .containsEntry("channel", "SMS")
                .containsEntry("template_code", "FEE_REMINDER")
                .containsEntry("status", "SENT")
                .containsEntry("context_type", "FEE_REMINDER")
                .containsEntry("context_id", STUDENT_FEE_ID);
        assertThat(notification.get("provider_message_id").toString())
                .startsWith("SMS-" + STUDENT_FEE_ID);
        assertThat(notification.get("provider_cost")).isEqualTo(new BigDecimal("4.0000"));
        assertThat(notification.get("correlation_id")).isNotNull();
        assertThat(notification.get("idempotency_key").toString()).startsWith("FEE_REMINDER:");
        assertThat(notification.get("rendered_body").toString()).contains("Hello Grace, pay 5000.00.");
    }

    @Test
    @DisplayName("Should prevent duplicate same-day bulk notification")
    void shouldPreventDuplicateSameDayBulkNotification() {
        seedBulkNotificationFixture();
        seedTemplate(SMS_TEMPLATE_ID, SCHOOL_ID, "FEE_REMINDER", "SMS", true, true);

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                java.util.List.of(STUDENT_FEE_ID), "FEE_REMINDER", "SMS");
        notificationService.sendBulkNotifications(request).block();

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.recipientsCount()).isEqualTo(1);
                    assertThat(response.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(response.status()).isEqualTo("FAILED");
                    assertThat(response.message()).isEqualTo("0 of 1 messages queued for delivery");
                })
                .verifyComplete();

        Long notificationCount = ((Number) fetchOne("""
                SELECT COUNT(*) AS count
                FROM notification.notifications
                WHERE context_id = :studentFeeId
                """, Map.of("studentFeeId", STUDENT_FEE_ID)).get("count")).longValue();
        assertThat(notificationCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject missing student fee during bulk notification")
    void shouldRejectMissingStudentFeeDuringBulkNotification() {
        seedSchool(SCHOOL_ID, "GIS");
        seedTemplate(SMS_TEMPLATE_ID, SCHOOL_ID, "FEE_REMINDER", "SMS", true, true);

        StepVerifier.create(notificationService.sendBulkNotifications(
                        new SendBulkNotificationRequest(
                                java.util.List.of(UUID.randomUUID()),
                                "FEE_REMINDER",
                                "SMS")))
                .assertNext(response -> {
                    assertThat(response.recipientsCount()).isEqualTo(1);
                    assertThat(response.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(response.status()).isEqualTo("FAILED");
                    assertThat(response.message()).isEqualTo("0 of 1 messages queued for delivery");
                })
                .verifyComplete();
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

    private void seedBulkNotificationFixture() {
        seedSchool(SCHOOL_ID, "GIS");
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

    private void seedReminderSchedule(UUID scheduleId, UUID schoolId, String name, boolean isActive) {
        databaseClient.sql("""
                INSERT INTO notification.reminder_schedules (
                    id, school_id, name, description, trigger_type, template_code,
                    days_offset, send_time, recipient_role, is_recurring,
                    recurring_interval_days, is_active, created_at
                )
                VALUES (
                    :scheduleId, :schoolId, :name, 'Send reminder before fee due date',
                    'BEFORE_DUE_DATE', 'FEE_REMINDER', 7, :sendTime, 'GUARDIAN',
                    false, NULL, :isActive, :createdAt
                )
                """)
                .bind("scheduleId", scheduleId)
                .bind("schoolId", schoolId)
                .bind("name", name)
                .bind("sendTime", LocalTime.of(9, 30))
                .bind("isActive", isActive)
                .bind("createdAt", Instant.parse("2026-06-05T10:00:00Z"))
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
        databaseClient.sql("DELETE FROM notification.reminder_schedules").fetch().rowsUpdated().block();
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

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }
}
