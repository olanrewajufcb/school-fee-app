package com.fee.app.schoolfeeapp.notification.service.impl;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianLinkRepository;
import com.fee.app.schoolfeeapp.auth.repository.StudentGuardianRepository;
import com.fee.app.schoolfeeapp.auth.util.JwtUtils;
import com.fee.app.schoolfeeapp.auth.util.SchoolFeeUser;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.fee.repository.StudentFeeRepository;
import com.fee.app.schoolfeeapp.notification.channel.NotificationChannelSelector;
import com.fee.app.schoolfeeapp.notification.domain.NotificationTemplate;
import com.fee.app.schoolfeeapp.notification.dto.request.UpdateTemplateRequest;
import com.fee.app.schoolfeeapp.notification.repository.NotificationRepository;
import com.fee.app.schoolfeeapp.notification.repository.NotificationTemplateRepository;
import com.fee.app.schoolfeeapp.notification.repository.ReminderScheduleRepository;
import com.fee.app.schoolfeeapp.student.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
