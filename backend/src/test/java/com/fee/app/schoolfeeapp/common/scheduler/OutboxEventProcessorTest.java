package com.fee.app.schoolfeeapp.common.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.service.GuardianInvitationService;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.fee.domain.FeeCategory;
import com.fee.app.schoolfeeapp.fee.repository.FeeCategoryRepository;
import com.fee.app.schoolfeeapp.notification.domain.NotificationTemplate;
import com.fee.app.schoolfeeapp.notification.repository.NotificationTemplateRepository;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private GuardianInvitationService invitationService;

    @Mock
    private KeycloakAdminServiceImpl keycloakAdminService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationTemplateRepository notificationTemplateRepository;

    @Mock
    private FeeCategoryRepository feeCategoryRepository;

    private ObjectMapper objectMapper;
    private OutboxEventProcessor processor;

    private static final UUID SCHOOL_ID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    private static final UUID ADMIN_KEYCLOAK_ID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String SCHOOL_NAME = "Grace International School";
    private static final String SCHOOL_CODE = "GIS";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        processor = new OutboxEventProcessor(
                outboxRepository,
                invitationService,
                objectMapper,
                keycloakAdminService,
                userRepository,
                notificationTemplateRepository,
                feeCategoryRepository);
    }

    @Test
    @DisplayName("Should send admin email and create missing school defaults")
    void shouldSendAdminEmailAndCreateMissingSchoolDefaults() throws Exception {
        when(notificationTemplateRepository.existsBySchoolIdAndTemplateCodeAndChannel(
                eq(SCHOOL_ID), anyString(), eq("SMS")))
                .thenReturn(Mono.just(false));
        when(notificationTemplateRepository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(feeCategoryRepository.existsBySchoolIdAndName(eq(SCHOOL_ID), anyString()))
                .thenReturn(Mono.just(false));
        when(feeCategoryRepository.save(any(FeeCategory.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(invokeHandleSchoolCreated(schoolCreatedEvent(ADMIN_KEYCLOAK_ID)))
                .verifyComplete();

        verify(keycloakAdminService).sendPasswordResetEmail(ADMIN_KEYCLOAK_ID.toString());

        ArgumentCaptor<NotificationTemplate> templateCaptor =
                ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(notificationTemplateRepository, org.mockito.Mockito.times(4)).save(templateCaptor.capture());
        assertThat(templateCaptor.getAllValues())
                .extracting(NotificationTemplate::getTemplateCode)
                .containsExactly(
                        "FEE_DUE_REMINDER",
                        "FEE_OVERDUE_NOTICE",
                        "PAYMENT_RECEIPT",
                        "PARENT_INVITATION");
        assertThat(templateCaptor.getAllValues())
                .allSatisfy(template -> {
                    assertThat(template.getSchoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(template.getChannel()).isEqualTo("SMS");
                    assertThat(template.getIsDefault()).isTrue();
                    assertThat(template.getCreatedAt()).isNotNull();
                    assertThat(template.getVariables().path("variables")).isNotEmpty();
                });

        ArgumentCaptor<FeeCategory> categoryCaptor = ArgumentCaptor.forClass(FeeCategory.class);
        verify(feeCategoryRepository, org.mockito.Mockito.times(8)).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getAllValues())
                .extracting(FeeCategory::getName)
                .containsExactly(
                        "Tuition",
                        "Registration",
                        "Examination",
                        "Books and Supplies",
                        "Uniform",
                        "Transport",
                        "Feeding",
                        "Boarding");
        assertThat(categoryCaptor.getAllValues())
                .allSatisfy(category -> {
                    assertThat(category.getSchoolId()).isEqualTo(SCHOOL_ID);
                    assertThat(category.getCreatedAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("Should skip admin email and not duplicate existing defaults")
    void shouldSkipAdminEmailAndNotDuplicateExistingDefaults() throws Exception {
        when(notificationTemplateRepository.existsBySchoolIdAndTemplateCodeAndChannel(
                eq(SCHOOL_ID), anyString(), eq("SMS")))
                .thenReturn(Mono.just(true));
        when(feeCategoryRepository.existsBySchoolIdAndName(eq(SCHOOL_ID), anyString()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(invokeHandleSchoolCreated(schoolCreatedEvent(null)))
                .verifyComplete();

        verify(keycloakAdminService, never()).sendPasswordResetEmail(anyString());
        verify(notificationTemplateRepository, never()).save(any(NotificationTemplate.class));
        verify(feeCategoryRepository, never()).save(any(FeeCategory.class));
    }

    @SuppressWarnings("unchecked")
    private Mono<Void> invokeHandleSchoolCreated(OutboxEvent event) throws Exception {
        Method method = OutboxEventProcessor.class.getDeclaredMethod("handleSchoolCreated", OutboxEvent.class);
        method.setAccessible(true);
        return (Mono<Void>) method.invoke(processor, event);
    }

    private OutboxEvent schoolCreatedEvent(UUID adminKeycloakId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("schoolId", SCHOOL_ID.toString());
        payload.put("schoolName", SCHOOL_NAME);
        payload.put("schoolCode", SCHOOL_CODE);
        payload.put("adminKeycloakId", adminKeycloakId == null ? null : adminKeycloakId.toString());
        payload.put("sessionId", UUID.randomUUID().toString());
        payload.put("termIds", java.util.List.of(UUID.randomUUID().toString()));

        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("SCHOOL_CREATED")
                .aggregateId(SCHOOL_ID)
                .aggregateType("SCHOOL")
                .payload(objectMapper.valueToTree(payload))
                .status("PROCESSING")
                .retryCount(0)
                .maxRetries(3)
                .nextRetryAt(Instant.now())
                .createdAt(Instant.now())
                .build();
    }
}
