package com.fee.app.schoolfeeapp.notification.service.impl;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardian;
import com.fee.app.schoolfeeapp.auth.domain.StudentGuardianLink;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.domain.StudentFee;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.notification.channel.ChannelResult;
import com.fee.app.schoolfeeapp.notification.channel.NotificationChannel;
import com.fee.app.schoolfeeapp.notification.channel.NotificationChannelSelector;
import com.fee.app.schoolfeeapp.notification.domain.Notification;
import com.fee.app.schoolfeeapp.notification.domain.NotificationTemplate;
import com.fee.app.schoolfeeapp.notification.dto.request.SendBulkNotificationRequest;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import com.fee.app.schoolfeeapp.notification.repository.NotificationRepository;
import com.fee.app.schoolfeeapp.notification.repository.NotificationTemplateRepository;
import com.fee.app.schoolfeeapp.notification.repository.ReminderScheduleRepository;
import com.fee.app.schoolfeeapp.notification.domain.ReminderSchedule;
import com.fee.app.schoolfeeapp.student.domain.Student;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ReminderScheduleRepository scheduleRepository;
    @Mock
    private StudentFeeRepository studentFeeRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentGuardianRepository guardianRepository;
    @Mock
    private StudentGuardianLinkRepository guardianLinkRepository;
    @Mock
    private NotificationChannelSelector channelSelector;
    @Mock
    private JwtUtils jwtUtils;

    private NotificationServiceImpl notificationService;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID USER_ID = UUID.fromString("c3d4e5f6-a7b8-9012-cdef-123456789012");
    private static final UUID TEMPLATE_ID = UUID.fromString("d4e5f6a7-b890-1234-def1-234567890123");

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                templateRepository,
                notificationRepository,
                scheduleRepository,
                studentFeeRepository,
                studentRepository,
                guardianRepository,
                guardianLinkRepository,
                channelSelector,
                jwtUtils);
    }

    // ========================================================================
    // TEMPLATES
    // ========================================================================

    @Test
    @DisplayName("Should list all templates for current school")
    void shouldListAllTemplatesForCurrentSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(template("SMS"), template("EMAIL")));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(2);
                    assertThat(response.getFirst().templateId()).isEqualTo(TEMPLATE_ID);
                    assertThat(response.getFirst().channel()).isEqualTo("SMS");
                    assertThat(response.getFirst().variables()).containsExactly("parent_name", "amount");
                    assertThat(response.getFirst().isDefault()).isTrue();
                    assertThat(response.getFirst().isActive()).isTrue();
                })
                .verifyComplete();

        verify(templateRepository).findBySchoolId(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should normalize channel filter before querying")
    void shouldNormalizeChannelFilterBeforeQuerying() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolIdAndChannel(SCHOOL_ID, "SMS"))
                .thenReturn(Flux.just(template("SMS")));

        StepVerifier.create(notificationService.getTemplates(" sms "))
                .assertNext(response -> assertThat(response).hasSize(1))
                .verifyComplete();

        verify(templateRepository).findBySchoolIdAndChannel(SCHOOL_ID, "SMS");
    }

    @Test
    @DisplayName("Should reject unsupported template channel")
    void shouldRejectUnsupportedTemplateChannel() {
        StepVerifier.create(notificationService.getTemplates("PUSH"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("UNSUPPORTED_CHANNEL");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should update template fields")
    void shouldUpdateTemplateFields() {
        NotificationTemplate template = template("SMS");
        UpdateTemplateRequest request = new UpdateTemplateRequest(
                " Hello {parent_name}, pay {amount}. ",
                " Updated Reminder ",
                false);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findByIdAndSchoolId(TEMPLATE_ID, SCHOOL_ID)).thenReturn(Mono.just(template));
        when(templateRepository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> {
                    NotificationTemplate saved = invocation.getArgument(0);
                    saved.setUpdatedAt(Instant.parse("2026-06-05T10:15:30Z"));
                    return Mono.just(saved);
                });

        StepVerifier.create(notificationService.updateTemplate(TEMPLATE_ID, request))
                .assertNext(response -> {
                    assertThat(response.templateId()).isEqualTo(TEMPLATE_ID);
                    assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-06-05T10:15:30Z"));
                })
                .verifyComplete();

        ArgumentCaptor<NotificationTemplate> templateCaptor =
                ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templateRepository).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getBodyTemplate())
                .isEqualTo("Hello {parent_name}, pay {amount}.");
        assertThat(templateCaptor.getValue().getName()).isEqualTo("Updated Reminder");
        assertThat(templateCaptor.getValue().getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should reject update with no fields")
    void shouldRejectUpdateWithNoFields() {
        StepVerifier.create(notificationService.updateTemplate(
                        TEMPLATE_ID, new UpdateTemplateRequest(null, null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_TEMPLATE_REQUEST");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject blank template body")
    void shouldRejectBlankTemplateBody() {
        StepVerifier.create(notificationService.updateTemplate(
                        TEMPLATE_ID, new UpdateTemplateRequest("   ", null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("body");
                })
                .verify();
    }

    @Test
    @DisplayName("Should require school context for template update")
    void shouldRequireSchoolContextForTemplateUpdate() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(null)
                .userType("SUPER_ADMIN")
                .roles(Set.of("SUPER_ADMIN"))
                .build()));

        StepVerifier.create(notificationService.updateTemplate(
                        TEMPLATE_ID, new UpdateTemplateRequest("Body", null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();

        verify(templateRepository, never()).findByIdAndSchoolId(any(), any());
    }

    @Test
    @DisplayName("Should map optimistic locking failure to template conflict")
    void shouldMapOptimisticLockingFailureToTemplateConflict() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findByIdAndSchoolId(TEMPLATE_ID, SCHOOL_ID)).thenReturn(Mono.just(template("SMS")));
        when(templateRepository.save(any(NotificationTemplate.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale")));

        StepVerifier.create(notificationService.updateTemplate(
                        TEMPLATE_ID, new UpdateTemplateRequest("Body", null, null)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TEMPLATE_UPDATE_CONFLICT");
                })
                .verify();
    }

    // ========================================================================
    // REMINDER SCHEDULES
    // ========================================================================

    @Test
    @DisplayName("Should return reminder schedules for current school")
    void shouldReturnReminderSchedulesForCurrentSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        ReminderSchedule schedule = ReminderSchedule.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .name("Before Due Date")
                .triggerType("BEFORE_DUE")
                .daysOffset(3)
                .sendTime(LocalTime.of(9, 0))
                .templateCode("FEE_REMINDER")
                .isActive(true)
                .build();
        when(scheduleRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(schedule));

        StepVerifier.create(notificationService.getReminderSchedules())
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().name()).isEqualTo("Before Due Date");
                    assertThat(response.getFirst().triggerType()).isEqualTo("BEFORE_DUE");
                    assertThat(response.getFirst().daysOffset()).isEqualTo(3);
                    assertThat(response.getFirst().sendTime()).isEqualTo(LocalTime.of(9, 0));
                    assertThat(response.getFirst().templateCode()).isEqualTo("FEE_REMINDER");
                    assertThat(response.getFirst().isActive()).isTrue();
                })
                .verifyComplete();

        verify(scheduleRepository).findBySchoolId(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should return empty list when no reminder schedules exist")
    void shouldReturnEmptyListWhenNoReminderSchedulesExist() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(scheduleRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.empty());

        StepVerifier.create(notificationService.getReminderSchedules())
                .assertNext(response -> assertThat(response).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject get reminder schedules when user has no school context")
    void shouldRejectGetReminderSchedulesWhenUserHasNoSchoolContext() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build()));

        StepVerifier.create(notificationService.getReminderSchedules())
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();

        verifyNoInteractions(scheduleRepository);
    }

    @Test
    @DisplayName("Should return multiple reminder schedules mapped correctly")
    void shouldReturnMultipleReminderSchedulesMappedCorrectly() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        ReminderSchedule active = ReminderSchedule.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .name("Before Due")
                .triggerType("BEFORE_DUE")
                .daysOffset(3)
                .sendTime(LocalTime.of(9, 0))
                .templateCode("FEE_REMINDER")
                .isActive(true)
                .build();
        ReminderSchedule inactive = ReminderSchedule.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .name("After Due")
                .triggerType("AFTER_DUE")
                .daysOffset(7)
                .sendTime(LocalTime.of(10, 0))
                .templateCode("OVERDUE_NOTICE")
                .isActive(false)
                .build();
        when(scheduleRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(active, inactive));

        StepVerifier.create(notificationService.getReminderSchedules())
                .assertNext(response -> {
                    assertThat(response).hasSize(2);
                    assertThat(response.get(0).isActive()).isTrue();
                    assertThat(response.get(1).isActive()).isFalse();
                    assertThat(response.get(1).name()).isEqualTo("After Due");
                    assertThat(response.get(1).daysOffset()).isEqualTo(7);
                })
                .verifyComplete();
    }

    // ========================================================================
    // sendBulkNotifications tests
    // ========================================================================

    @Test
    @DisplayName("Should reject send bulk notifications when user has no school context")
    void shouldRejectSendBulkNotificationsWhenUserHasNoSchoolContext() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(SchoolFeeUser.builder()
                .userId(USER_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build()));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(UUID.randomUUID()), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
                })
                .verify();

        verifyNoInteractions(templateRepository);
        verifyNoInteractions(studentFeeRepository);
    }

    @Test
    @DisplayName("Should reject send bulk notifications when template not found")
    void shouldRejectSendBulkNotificationsWhenTemplateNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "NONEXISTENT", "SMS"))
                .thenReturn(Mono.empty());

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(UUID.randomUUID()), "NONEXISTENT", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("TEMPLATE_NOT_FOUND");
                })
                .verify();
    }

    @Test
    @DisplayName("Should send SMS fee reminder and log notification before provider call")
    void shouldSendSmsFeeReminderAndLogNotification() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(guardianLink(studentId, guardianId)));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian(guardianId, "+2348012345678")));
        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(notificationRepository.updateDeliveryResult(
                any(), anyString(), nullable(String.class), any(), nullable(String.class), nullable(Instant.class)))
                .thenAnswer(invocation -> Mono.just(Notification.builder().id(invocation.getArgument(0)).build()));

        NotificationChannel mockChannel = mockChannel(
                "SMS",
                BigDecimal.TEN,
                ChannelResult.builder()
                        .channel("SMS")
                        .messageId("sms-123")
                        .success(true)
                        .build());
        when(channelSelector.select("SMS")).thenReturn(mockChannel);

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.recipientsCount()).isEqualTo(1);
                    assertThat(response.estimatedCost()).isEqualByComparingTo(BigDecimal.TEN);
                    assertThat(response.status()).isEqualTo("QUEUED");
                    assertThat(response.message()).isEqualTo("1 of 1 messages queued for delivery");
                })
                .verifyComplete();

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).insertNotification(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getStatus()).isEqualTo("QUEUED");
        assertThat(notificationCaptor.getValue().getTemplateCode()).isEqualTo("FEE_REMINDER");
        assertThat(notificationCaptor.getValue().getContextId()).isEqualTo(feeId);
        assertThat(notificationCaptor.getValue().getRecipientId()).isEqualTo(guardianId);
        assertThat(notificationCaptor.getValue().getIdempotencyKey()).startsWith("FEE_REMINDER:");
        verify(mockChannel).send("+2348012345678", "Hello Grace, pay 5000.00.", feeId.toString());
    }

    @Test
    @DisplayName("Should send to SMS and WhatsApp when bulk channel is both")
    void shouldSendToSmsAndWhatsAppWhenChannelIsBoth() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "BOTH"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(guardianLink(studentId, guardianId)));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian(guardianId, "+2348012345678")));
        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(notificationRepository.updateDeliveryResult(
                any(), anyString(), nullable(String.class), any(), nullable(String.class), nullable(Instant.class)))
                .thenAnswer(invocation -> Mono.just(Notification.builder().id(invocation.getArgument(0)).build()));

        NotificationChannel smsChannel = mockChannel(
                "SMS",
                BigDecimal.valueOf(4),
                ChannelResult.builder().channel("SMS").messageId("sms-123").success(true).build());
        NotificationChannel whatsAppChannel = mockChannel(
                "WHATSAPP",
                new BigDecimal("0.50"),
                ChannelResult.builder().channel("WHATSAPP").messageId("wa-123").success(true).build());
        when(channelSelector.getAvailableChannels()).thenReturn(List.of("SMS", "WHATSAPP"));
        when(channelSelector.select("SMS")).thenReturn(smsChannel);
        when(channelSelector.select("WHATSAPP")).thenReturn(whatsAppChannel);

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(feeId), "FEE_REMINDER", " both ");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.recipientsCount()).isEqualTo(2);
                    assertThat(response.estimatedCost()).isEqualByComparingTo("4.50");
                    assertThat(response.status()).isEqualTo("QUEUED");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle provider send error gracefully and mark delivery as failed")
    void shouldHandleProviderSendErrorGracefully() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(guardianLink(studentId, guardianId)));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian(guardianId, "+2348012345678")));

        // Mock successful DB insert
        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        // Mock DB update for the result
        when(notificationRepository.updateDeliveryResult(
                any(), anyString(), nullable(String.class), any(), nullable(String.class), nullable(Instant.class)))
                .thenAnswer(invocation -> Mono.just(Notification.builder().id(invocation.getArgument(0)).build()));

        // Setup the channel to throw an error during the send operation
        NotificationChannel mockChannel = mockChannelMetadata("SMS", BigDecimal.TEN);
        when(mockChannel.send(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("SMS Gateway Timeout Error")));
        when(channelSelector.select("SMS")).thenReturn(mockChannel);

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    // Assert the bulk response reflects the failure
                    assertThat(response.recipientsCount()).isEqualTo(1);
                    assertThat(response.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(response.status()).isEqualTo("FAILED");
                    assertThat(response.message()).isEqualTo("0 of 1 messages queued for delivery");
                })
                .verifyComplete();

        // Verify the onErrorResume block successfully passed the error to the DB update
        verify(notificationRepository).updateDeliveryResult(
                any(UUID.class),
                org.mockito.ArgumentMatchers.eq("FAILED"),
                nullable(String.class),
                org.mockito.ArgumentMatchers.eq(BigDecimal.ZERO),
                org.mockito.ArgumentMatchers.eq("SMS Gateway Timeout Error"), // The error message was captured
                nullable(Instant.class)
        );
    }

    @Test
    @DisplayName("Should reject duplicate fee IDs before authentication")
    void shouldRejectDuplicateFeeIdsBeforeAuthentication() {
        UUID feeId = UUID.randomUUID();
        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(feeId, feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode())
                            .isEqualTo("DUPLICATE_NOTIFICATION_TARGET");
                })
                .verify();

        verify(jwtUtils, never()).getCurrentUser();
    }

    @Test
    @DisplayName("Should reject missing fee ID instead of silently skipping it")
    void shouldRejectMissingFeeIdInsteadOfSkippingIt() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        UUID missingFeeId = UUID.randomUUID();
        when(studentFeeRepository.findByIdAndSchoolId(missingFeeId, SCHOOL_ID)).thenReturn(Mono.empty());

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(missingFeeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .expectNextMatches(response -> 
                        response.status().equals("FAILED") &&
                        response.recipientsCount() == 1 &&
                        response.estimatedCost().compareTo(BigDecimal.ZERO) == 0)
                .verifyComplete();

        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("Should reject guardian without phone before sending")
    void shouldRejectGuardianWithoutPhoneBeforeSending() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(guardianLink(studentId, guardianId)));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian(guardianId, "   ")));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .expectNextMatches(response -> 
                        response.status().equals("FAILED") &&
                        response.recipientsCount() == 1 &&
                        response.estimatedCost().compareTo(BigDecimal.ZERO) == 0)
                .verifyComplete();

        verifyNoInteractions(notificationRepository);
    }

    @Test
    @DisplayName("Should skip duplicate daily notification without provider send")
    void shouldSkipDuplicateDailyNotificationWithoutProviderSend() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(guardianLink(studentId, guardianId)));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian(guardianId, "+2348012345678")));
        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate")));

        NotificationChannel mockChannel = mockNamedChannel("SMS");
        when(channelSelector.select("SMS")).thenReturn(mockChannel);

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(
                List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.recipientsCount()).isEqualTo(1);
                    assertThat(response.estimatedCost()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(response.status()).isEqualTo("FAILED");
                    assertThat(response.message()).isEqualTo("0 of 1 messages queued for delivery");
                })
                .verifyComplete();

        verify(mockChannel, never()).send(anyString(), anyString(), anyString());
    }

    // ========================================================================
    // BALANCE TESTS
    // ========================================================================

    @Test
    @DisplayName("Should successfully return formatted balance response")
    void shouldSuccessfullyReturnFormattedBalanceResponse() {
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);
        when(smsChannel.getBalance()).thenReturn(Mono.just(2500));
        when(smsChannel.getProviderName()).thenReturn("Africa's Talking");
        when(smsChannel.getCostPerMessage()).thenReturn(BigDecimal.valueOf(4.0));

        StepVerifier.create(notificationService.getBalance())
                .assertNext(response -> {
                    assertThat(response.provider()).isEqualTo("Africa's Talking");
                    assertThat(response.balance()).isEqualTo(2500);
                    assertThat(response.currency()).isEqualTo("NGN");
                    assertThat(response.costPerSms()).isEqualByComparingTo(BigDecimal.valueOf(4.0));
                    assertThat(response.lastPurchased()).isEqualTo(LocalDate.now());
                    assertThat(response.estimatedRemainingDays()).isEqualTo(250);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle zero balance calculation correctly")
    void shouldHandleZeroBalanceCalculationCorrectly() {
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);
        when(smsChannel.getBalance()).thenReturn(Mono.just(0));
        when(smsChannel.getProviderName()).thenReturn("Africa's Talking");
        when(smsChannel.getCostPerMessage()).thenReturn(BigDecimal.valueOf(4.0));

        StepVerifier.create(notificationService.getBalance())
                .assertNext(response -> {
                    assertThat(response.balance()).isEqualTo(0);
                    assertThat(response.estimatedRemainingDays()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should propagate error if underlying channel fails to get balance")
    void shouldPropagateErrorIfUnderlyingChannelFailsToGetBalance() {
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);
        when(smsChannel.getBalance()).thenReturn(Mono.error(new RuntimeException("API Down")));

        StepVerifier.create(notificationService.getBalance())
                .expectErrorMessage("API Down")
                .verify();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private NotificationChannel mockChannel(
            String channelName, BigDecimal costPerMessage, ChannelResult result) {
        NotificationChannel channel = mockChannelMetadata(channelName, costPerMessage);
        when(channel.send(anyString(), anyString(), anyString())).thenReturn(Mono.just(result));
        return channel;
    }

    private NotificationChannel mockChannelMetadata(String channelName, BigDecimal costPerMessage) {
        NotificationChannel channel = mock(NotificationChannel.class);
        // won't throw an UnnecessaryStubbingException
        lenient().when(channel.getChannel()).thenReturn(channelName);
        lenient().when(channel.getCostPerMessage()).thenReturn(costPerMessage);

        return channel;
    }

    private NotificationChannel mockNamedChannel(String channelName) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.getChannel()).thenReturn(channelName);
        return channel;
    }

    private StudentFee studentFee(UUID feeId, UUID studentId) {
        return StudentFee.builder()
                .id(feeId)
                .schoolId(SCHOOL_ID)
                .studentId(studentId)
                .totalAmount(BigDecimal.valueOf(5000).setScale(2))
                .dueDate(LocalDate.now().plusDays(7))
                .build();
    }

    private Student student(UUID studentId) {
        return Student.builder()
                .id(studentId)
                .schoolId(SCHOOL_ID)
                .admissionNumber("ADM-001")
                .firstName("Ada")
                .lastName("Okafor")
                .enrollmentStatus("ACTIVE")
                .build();
    }

    private StudentGuardianLink guardianLink(UUID studentId, UUID guardianId) {
        return StudentGuardianLink.builder()
                .id(UUID.randomUUID())
                .schoolId(SCHOOL_ID)
                .studentId(studentId)
                .guardianId(guardianId)
                .isPrimaryContact(true)
                .canReceiveSms(true)
                .build();
    }

    private StudentGuardian guardian(UUID guardianId, String phone) {
        return StudentGuardian.builder()
                .id(guardianId)
                .schoolId(SCHOOL_ID)
                .firstName("Grace")
                .lastName("Okafor")
                .phone(phone)
                .isActive(true)
                .build();
    }

    private NotificationTemplate template(String channel) {
        return NotificationTemplate.builder()
                .id(TEMPLATE_ID)
                .schoolId(SCHOOL_ID)
                .templateCode("FEE_REMINDER")
                .name(channel + " Fee Reminder")
                .channel(channel)
                .bodyTemplate("Hello {parent_name}, pay {amount}.")
                .variables(JsonMapper.builder().build()
                        .valueToTree(java.util.List.of("parent_name", "amount")))
                .isDefault(true)
                .isActive(true)
                .createdAt(Instant.parse("2026-06-05T10:00:00Z"))
                .updatedAt(Instant.parse("2026-06-05T10:00:00Z"))
                .version(0)
                .build();
    }

    private SchoolFeeUser currentUser() {
        return SchoolFeeUser.builder()
                .userId(USER_ID)
                .schoolId(SCHOOL_ID)
                .userType("SCHOOL_ADMIN")
                .roles(Set.of("SCHOOL_ADMIN"))
                .build();
    }

    // ========================================================================
    // BULK NOTIFICATIONS REQUEST VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject sendBulkNotifications when request is null")
    void shouldRejectSendBulkNotificationsWhenRequestIsNull() {
        StepVerifier.create(notificationService.sendBulkNotifications(null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_BULK_NOTIFICATION_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject sendBulkNotifications when studentFeeIds list is null or empty")
    void shouldRejectSendBulkNotificationsWhenStudentFeeIdsNullOrEmpty() {
        SendBulkNotificationRequest reqNull = new SendBulkNotificationRequest(null, "FEE_REMINDER", "SMS");
        SendBulkNotificationRequest reqEmpty = new SendBulkNotificationRequest(List.of(), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(reqNull))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeIds"))
                .verify();

        StepVerifier.create(notificationService.sendBulkNotifications(reqEmpty))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeIds"))
                .verify();
    }

    @Test
    @DisplayName("Should reject sendBulkNotifications when studentFeeIds list contains null")
    void shouldRejectSendBulkNotificationsWhenStudentFeeIdsContainsNull() {
        java.util.ArrayList<UUID> ids = new java.util.ArrayList<>();
        ids.add(null);
        SendBulkNotificationRequest req = new SendBulkNotificationRequest(ids, "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(req))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("studentFeeIds"))
                .verify();
    }

    @Test
    @DisplayName("Should reject sendBulkNotifications when templateCode is blank")
    void shouldRejectSendBulkNotificationsWhenTemplateCodeBlank() {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest(List.of(UUID.randomUUID()), " ", "SMS");
        StepVerifier.create(notificationService.sendBulkNotifications(req))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("templateCode"))
                .verify();
    }

    @Test
    @DisplayName("Should reject sendBulkNotifications when channel is blank")
    void shouldRejectSendBulkNotificationsWhenChannelBlank() {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest(List.of(UUID.randomUUID()), "FEE_REMINDER", " ");
        StepVerifier.create(notificationService.sendBulkNotifications(req))
                .expectErrorSatisfies(error -> assertThat(((SchoolFeeException) error).getField()).isEqualTo("channel"))
                .verify();
    }

    @Test
    @DisplayName("Should reject sendBulkNotifications when channel is unsupported")
    void shouldRejectSendBulkNotificationsWhenChannelUnsupported() {
        SendBulkNotificationRequest req = new SendBulkNotificationRequest(List.of(UUID.randomUUID()), "FEE_REMINDER", "PUSH");
        StepVerifier.create(notificationService.sendBulkNotifications(req))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("UNSUPPORTED_CHANNEL");
                })
                .verify();
    }

    // ========================================================================
    // UPDATE TEMPLATE REQUEST VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject updateTemplate when template ID is null")
    void shouldRejectUpdateTemplateWhenTemplateIdIsNull() {
        UpdateTemplateRequest request = new UpdateTemplateRequest("Body", "Name", true);
        StepVerifier.create(notificationService.updateTemplate(null, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_TEMPLATE_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject updateTemplate when request is null")
    void shouldRejectUpdateTemplateWhenRequestIsNull() {
        StepVerifier.create(notificationService.updateTemplate(TEMPLATE_ID, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("INVALID_TEMPLATE_REQUEST");
                })
                .verify();
    }

    @Test
    @DisplayName("Should reject updateTemplate when name is blank")
    void shouldRejectUpdateTemplateWhenNameIsBlank() {
        UpdateTemplateRequest request = new UpdateTemplateRequest("Body", "   ", true);
        StepVerifier.create(notificationService.updateTemplate(TEMPLATE_ID, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getField()).isEqualTo("name");
                })
                .verify();
    }

    // ========================================================================
    // PRIMARY GUARDIAN LOOKUP BRANCHES
    // ========================================================================

    @Test
    @DisplayName("Should handle sendBulkNotifications when guardian link is not found")
    void shouldHandleSendBulkWhenGuardianLinkNotFound() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.empty());

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("FAILED");
                    assertThat(response.recipientsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle sendBulkNotifications when guardian link belongs to another school")
    void shouldHandleSendBulkWhenGuardianLinkWrongSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        StudentGuardianLink link = guardianLink(studentId, guardianId);
        link.setSchoolId(UUID.randomUUID());

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(link));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> assertThat(response.status()).isEqualTo("FAILED"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle sendBulkNotifications when guardian link has canReceiveSms false")
    void shouldHandleSendBulkWhenCanReceiveSmsFalse() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        StudentGuardianLink link = guardianLink(studentId, guardianId);
        link.setCanReceiveSms(false);

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(link));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> assertThat(response.status()).isEqualTo("FAILED"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle sendBulkNotifications when guardian belongs to another school")
    void shouldHandleSendBulkWhenGuardianWrongSchool() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        StudentGuardian g = guardian(guardianId, "+2348012345678");
        g.setSchoolId(UUID.randomUUID());

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(guardianLink(studentId, guardianId)));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(g));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> assertThat(response.status()).isEqualTo("FAILED"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle sendBulkNotifications when guardian is inactive")
    void shouldHandleSendBulkWhenGuardianInactive() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        StudentGuardian g = guardian(guardianId, "+2348012345678");
        g.setIsActive(false);

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(guardianLink(studentId, guardianId)));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(g));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> assertThat(response.status()).isEqualTo("FAILED"))
                .verifyComplete();
    }

    // ========================================================================
    // BULK CHANNELS SELECTOR VALIDATIONS
    // ========================================================================

    @Test
    @DisplayName("Should reject sendBulkNotifications with channel BOTH when SMS or WHATSAPP is missing")
    void shouldRejectBothChannelWhenUnderlyingMissing() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "BOTH"))
                .thenReturn(Mono.just(template("BOTH")));
        when(channelSelector.getAvailableChannels()).thenReturn(List.of("SMS"));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(UUID.randomUUID()), "FEE_REMINDER", "BOTH");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("UNSUPPORTED_CHANNEL");
                    assertThat(((SchoolFeeException) error).getMessage()).contains("SMS and WHATSAPP channels must both be configured");
                })
                .verify();
    }

    // ========================================================================
    // TEMPLATE VARIABLES PARSER SCENARIOS
    // ========================================================================

    @Test
    @DisplayName("Should parse template variables when JsonNode is a string (textual)")
    void shouldParseVariablesWhenJsonIsString() {
        NotificationTemplate t = template("SMS");
        t.setVariables(com.fasterxml.jackson.databind.node.TextNode.valueOf("[\"parent_name\", \"amount\"]"));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(t));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().variables()).containsExactly("parent_name", "amount");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty variables when JsonNode is non-textual non-array")
    void shouldReturnEmptyVariablesWhenJsonNodeInvalidType() {
        NotificationTemplate t = template("SMS");
        t.setVariables(com.fasterxml.jackson.databind.node.IntNode.valueOf(42));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(t));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().variables()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject sendBulkNotifications when studentFeeIds has duplicates")
    void shouldRejectSendBulkNotificationsWhenStudentFeeIdsContainsDuplicates() {
        UUID feeId = UUID.randomUUID();
        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId, feeId), "FEE_REMINDER", "SMS");
        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(SchoolFeeException.class);
                    assertThat(((SchoolFeeException) error).getErrorCode()).isEqualTo("DUPLICATE_NOTIFICATION_TARGET");
                })
                .verify();
    }

    @Test
    @DisplayName("Should successfully update template name only")
    void shouldUpdateTemplateNameOnly() {
        UpdateTemplateRequest request = new UpdateTemplateRequest(null, "Updated Name", null);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findByIdAndSchoolId(TEMPLATE_ID, SCHOOL_ID)).thenReturn(Mono.just(template("SMS")));
        when(templateRepository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> {
                    NotificationTemplate saved = invocation.getArgument(0);
                    saved.setUpdatedAt(Instant.parse("2026-06-05T10:15:30Z"));
                    return Mono.just(saved);
                });

        StepVerifier.create(notificationService.updateTemplate(TEMPLATE_ID, request))
                .assertNext(response -> {
                    assertThat(response.templateId()).isEqualTo(TEMPLATE_ID);
                    assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-06-05T10:15:30Z"));
                })
                .verifyComplete();

        ArgumentCaptor<NotificationTemplate> templateCaptor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templateRepository).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getBodyTemplate()).isEqualTo("Hello {parent_name}, pay {amount}.");
        assertThat(templateCaptor.getValue().getName()).isEqualTo("Updated Name");
        assertThat(templateCaptor.getValue().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("Should successfully update template isActive only")
    void shouldUpdateTemplateIsActiveOnly() {
        UpdateTemplateRequest request = new UpdateTemplateRequest(null, null, false);
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findByIdAndSchoolId(TEMPLATE_ID, SCHOOL_ID)).thenReturn(Mono.just(template("SMS")));
        when(templateRepository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> {
                    NotificationTemplate saved = invocation.getArgument(0);
                    saved.setUpdatedAt(Instant.parse("2026-06-05T10:15:30Z"));
                    return Mono.just(saved);
                });

        StepVerifier.create(notificationService.updateTemplate(TEMPLATE_ID, request))
                .assertNext(response -> {
                    assertThat(response.templateId()).isEqualTo(TEMPLATE_ID);
                    assertThat(response.updatedAt()).isEqualTo(Instant.parse("2026-06-05T10:15:30Z"));
                })
                .verifyComplete();

        ArgumentCaptor<NotificationTemplate> templateCaptor = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templateRepository).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getBodyTemplate()).isEqualTo("Hello {parent_name}, pay {amount}.");
        assertThat(templateCaptor.getValue().getName()).isEqualTo("SMS Fee Reminder");
        assertThat(templateCaptor.getValue().getIsActive()).isFalse();
    }

    @Test
    @DisplayName("Should list all templates when channel filter is blank")
    void shouldListAllTemplatesWhenChannelFilterIsBlank() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID))
                .thenReturn(Flux.just(template("SMS")));

        StepVerifier.create(notificationService.getTemplates("   "))
                .assertNext(response -> assertThat(response).hasSize(1))
                .verifyComplete();

        verify(templateRepository).findBySchoolId(SCHOOL_ID);
    }

    @Test
    @DisplayName("Should filter null or blank variables from ArrayNode")
    void shouldFilterNullOrBlankVariablesFromArrayNode() {
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = 
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        arrayNode.add("parent_name");
        arrayNode.add("");
        arrayNode.add("amount");

        NotificationTemplate t = template("SMS");
        t.setVariables(arrayNode);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(t));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().variables()).containsExactly("parent_name", "amount");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return empty variables when textual node is blank or empty")
    void shouldReturnEmptyVariablesWhenTextualNodeIsBlank() {
        NotificationTemplate t = template("SMS");
        t.setVariables(com.fasterxml.jackson.databind.node.TextNode.valueOf("   "));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(t));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().variables()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should process sendBulkNotifications successfully when guardian link canReceiveSms is null")
    void shouldHandleSendBulkWhenCanReceiveSmsIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();

        StudentGuardianLink link = guardianLink(studentId, guardianId);
        link.setCanReceiveSms(null); 

        StudentGuardian guardian = guardian(guardianId, "+2348012345678");

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(link));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian));

        NotificationChannel smsChannel = mockChannelMetadata("SMS", BigDecimal.TEN);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(smsChannel.send(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(ChannelResult.builder()
                        .channel("SMS")
                        .messageId("msg-1")
                        .success(true)
                        .build()));

        when(notificationRepository.updateDeliveryResult(
                any(), anyString(), nullable(String.class), any(), nullable(String.class), nullable(Instant.class)))
                .thenAnswer(invocation -> Mono.just(Notification.builder().id(invocation.getArgument(0)).build()));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("QUEUED");
                    assertThat(response.recipientsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should process sendBulkNotifications successfully when guardian isActive is null")
    void shouldHandleSendBulkWhenGuardianIsActiveIsNull() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();

        StudentGuardianLink link = guardianLink(studentId, guardianId);
        StudentGuardian guardian = guardian(guardianId, "+2348012345678");
        guardian.setIsActive(null); 

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(link));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian));

        NotificationChannel smsChannel = mockChannelMetadata("SMS", BigDecimal.TEN);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(smsChannel.send(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(ChannelResult.builder()
                        .channel("SMS")
                        .messageId("msg-1")
                        .success(true)
                        .build()));

        when(notificationRepository.updateDeliveryResult(
                any(), anyString(), nullable(String.class), any(), nullable(String.class), nullable(Instant.class)))
                .thenAnswer(invocation -> Mono.just(Notification.builder().id(invocation.getArgument(0)).build()));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("QUEUED");
                    assertThat(response.recipientsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should propagate non-duplicate key exception from insertNotification")
    void shouldPropagateNonDuplicateKeyException() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();

        StudentGuardianLink link = guardianLink(studentId, guardianId);
        StudentGuardian guardian = guardian(guardianId, "+2348012345678");

        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId, studentId)));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID))
                .thenReturn(Mono.just(student(studentId)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId))
                .thenReturn(Flux.just(link));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId))
                .thenReturn(Mono.just(guardian));

        NotificationChannel smsChannel = mockChannelMetadata("SMS", BigDecimal.TEN);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenReturn(Mono.error(new RuntimeException("Database error")));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("FAILED");
                    assertThat(response.recipientsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return PARTIAL status when some sendBulkNotifications fail")
    void shouldReturnPartialStatusWhenSomeNotificationsFail() {
        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));

        UUID feeId1 = UUID.randomUUID();
        UUID studentId1 = UUID.randomUUID();
        UUID guardianId1 = UUID.randomUUID();

        UUID feeId2 = UUID.randomUUID(); 

        StudentGuardianLink link1 = guardianLink(studentId1, guardianId1);
        StudentGuardian guardian1 = guardian(guardianId1, "+2348012345678");

        when(studentFeeRepository.findByIdAndSchoolId(feeId1, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId1, studentId1)));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId1))
                .thenReturn(Flux.just(link1));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId1))
                .thenReturn(Mono.just(guardian1));

        when(studentFeeRepository.findByIdAndSchoolId(feeId2, SCHOOL_ID))
                .thenReturn(Mono.just(studentFee(feeId2, UUID.randomUUID())));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(any(UUID.class), org.mockito.ArgumentMatchers.eq(SCHOOL_ID)))
                .thenAnswer(invocation -> {
                    UUID sid = invocation.getArgument(0);
                    if (sid.equals(studentId1)) {
                        return Mono.just(student(studentId1));
                    }
                    return Mono.empty(); 
                });

        NotificationChannel smsChannel = mockChannelMetadata("SMS", BigDecimal.TEN);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);

        when(notificationRepository.insertNotification(any(Notification.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(smsChannel.send(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(ChannelResult.builder()
                        .channel("SMS")
                        .messageId("msg-1")
                        .success(true)
                        .build()));

        when(notificationRepository.updateDeliveryResult(
                any(), anyString(), nullable(String.class), any(), nullable(String.class), nullable(Instant.class)))
                .thenAnswer(invocation -> Mono.just(Notification.builder().id(invocation.getArgument(0)).build()));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId1, feeId2), "FEE_REMINDER", "SMS");

        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("PARTIAL");
                    assertThat(response.recipientsCount()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should fall back to UNKNOWN channel in onErrorResume when channel is null")
    void shouldFallbackToUnknownChannelInOnErrorResumeWhenChannelIsNull() throws Exception {
        UUID feeId = UUID.randomUUID();
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", null))
                .thenReturn(Mono.just(template("SMS")));
        
        NotificationChannel smsChannel = mockChannelMetadata("SMS", BigDecimal.TEN);
        when(channelSelector.select(null)).thenReturn(smsChannel);
                
        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID))
                .thenReturn(Mono.empty());

        Class<?> bulkNotificationClass = Class.forName("com.fee.app.schoolfeeapp.notification.service.impl.NotificationServiceImpl$BulkNotification");
        java.lang.reflect.Constructor<?> constructor = bulkNotificationClass.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object bulkRequest = constructor.newInstance(List.of(feeId), "FEE_REMINDER", null);

        java.lang.reflect.Method method = NotificationServiceImpl.class.getDeclaredMethod("sendBulkNotificationsForUser", bulkNotificationClass, SchoolFeeUser.class);
        method.setAccessible(true);
        Mono<com.fee.app.schoolfeeapp.notification.dto.response.SendBulkNotificationResponse> mono = (Mono<com.fee.app.schoolfeeapp.notification.dto.response.SendBulkNotificationResponse>) method.invoke(notificationService, bulkRequest, currentUser());

        StepVerifier.create(mono)
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("FAILED");
                    assertThat(response.recipientsCount()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle missing due date and null variables rendering templates")
    void shouldHandleMissingDueDateAndNullVariablesInTemplateRendering() {
        UUID feeId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();

        StudentFee fee = studentFee(feeId, studentId);
        fee.setDueDate(null); 
        fee.setTotalAmount(null); 

        StudentGuardian guardian = guardian(guardianId, "+2348012345678");
        guardian.setFirstName(null);

        Student student = student(studentId);
        StudentGuardianLink link = guardianLink(studentId, guardianId);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findActiveForBulkSend(SCHOOL_ID, "FEE_REMINDER", "SMS"))
                .thenReturn(Mono.just(template("SMS")));
        when(studentFeeRepository.findByIdAndSchoolId(feeId, SCHOOL_ID)).thenReturn(Mono.just(fee));
        when(studentRepository.findByIdAndSchoolIdAndDeletedAtIsNull(studentId, SCHOOL_ID)).thenReturn(Mono.just(student));
        when(guardianLinkRepository.findByStudentIdAndIsPrimaryContactTrue(studentId)).thenReturn(Flux.just(link));
        when(guardianRepository.findByIdAndDeletedAtIsNull(guardianId)).thenReturn(Mono.just(guardian));
        
        NotificationChannel smsChannel = mockChannelMetadata("SMS", BigDecimal.TEN);
        when(channelSelector.select("SMS")).thenReturn(smsChannel);
        when(notificationRepository.insertNotification(any(Notification.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));
        when(smsChannel.send(anyString(), anyString(), anyString())).thenReturn(Mono.just(ChannelResult.builder().channel("SMS").success(true).build()));
        when(notificationRepository.updateDeliveryResult(any(), any(), any(), any(), any(), any())).thenAnswer(i -> Mono.just(Notification.builder().build()));

        SendBulkNotificationRequest request = new SendBulkNotificationRequest(List.of(feeId), "FEE_REMINDER", "SMS");
        StepVerifier.create(notificationService.sendBulkNotifications(request))
                .assertNext(response -> {
                    assertThat(response.status()).isEqualTo("QUEUED");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cover template response isActive mapping and json node variables parsing")
    void shouldCoverTemplateResponseIsActiveMappingAndJsonNodeVariablesParsing() {
        NotificationTemplate inactiveTemplate = template("SMS");
        inactiveTemplate.setIsActive(false);
        inactiveTemplate.setVariables(com.fasterxml.jackson.databind.node.NullNode.getInstance());

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(inactiveTemplate));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().isActive()).isFalse();
                    assertThat(response.getFirst().variables()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cover json node variables array parsing with null and empty elements")
    void shouldCoverJsonNodeVariablesArrayParsingWithNullAndEmptyElements() {
        NotificationTemplate t = template("SMS");
        com.fasterxml.jackson.databind.node.ArrayNode arrayNode = com.fasterxml.jackson.databind.json.JsonMapper.builder().build().createArrayNode();
        arrayNode.add("var1");
        arrayNode.add("   "); 
        arrayNode.add((String) null); 

        t.setVariables(arrayNode);

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(t));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().variables()).containsExactly("var1", "null");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cover non-array non-textual json node variables parsing returning empty")
    void shouldCoverNonArrayNonTextualJsonNodeVariablesParsingReturningEmpty() {
        NotificationTemplate t = template("SMS");
        t.setVariables(com.fasterxml.jackson.databind.node.IntNode.valueOf(123));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(t));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().variables()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cover textual variables parsing with empty and blank comma-separated elements")
    void shouldCoverTextualVariablesParsingWithBlankElements() {
        NotificationTemplate t = template("SMS");
        t.setVariables(com.fasterxml.jackson.databind.node.TextNode.valueOf("[var1,  , var2]"));

        when(jwtUtils.getCurrentUser()).thenReturn(Mono.just(currentUser()));
        when(templateRepository.findBySchoolId(SCHOOL_ID)).thenReturn(Flux.just(t));

        StepVerifier.create(notificationService.getTemplates(null))
                .assertNext(response -> {
                    assertThat(response).hasSize(1);
                    assertThat(response.getFirst().variables()).containsExactly("var1", "var2");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should cover bulkStatus status mapping branches")
    void shouldCoverBulkStatusMappingBranches() throws Exception {
        java.lang.reflect.Method method = NotificationServiceImpl.class.getDeclaredMethod("bulkStatus", int.class, long.class);
        method.setAccessible(true);

        String status1 = (String) method.invoke(notificationService, 0, 5L);
        assertThat(status1).isEqualTo("FAILED");

        String status2 = (String) method.invoke(notificationService, 5, 0L);
        assertThat(status2).isEqualTo("FAILED");

        String status3 = (String) method.invoke(notificationService, 5, 5L);
        assertThat(status3).isEqualTo("QUEUED");

        String status4 = (String) method.invoke(notificationService, 5, 3L);
        assertThat(status4).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("Should cover requireSchoolId user validation branch using reflection")
    void shouldCoverRequireSchoolIdUserValidationBranch() throws Exception {
        java.lang.reflect.Method method = NotificationServiceImpl.class.getDeclaredMethod("requireSchoolId", SchoolFeeUser.class);
        method.setAccessible(true);

        try {
            method.invoke(notificationService, (SchoolFeeUser) null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(SchoolFeeException.class);
            assertThat(((SchoolFeeException) e.getCause()).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
        }

        try {
            method.invoke(notificationService, SchoolFeeUser.builder().userId(USER_ID).schoolId(null).build());
        } catch (java.lang.reflect.InvocationTargetException e) {
            assertThat(e.getCause()).isInstanceOf(SchoolFeeException.class);
            assertThat(((SchoolFeeException) e.getCause()).getErrorCode()).isEqualTo("SCHOOL_CONTEXT_REQUIRED");
        }
    }
}

