package com.fee.app.schoolfeeapp.common.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fee.app.schoolfeeapp.auth.dto.response.KeycloakUserResult;
import com.fee.app.schoolfeeapp.auth.domain.User; // Ensure this matches your User domain/entity package
import com.fee.app.schoolfeeapp.auth.repository.UserRepository;
import com.fee.app.schoolfeeapp.auth.service.GuardianInvitationService;
import com.fee.app.schoolfeeapp.auth.service.impl.KeycloakAdminServiceImpl;
import com.fee.app.schoolfeeapp.common.domain.OutboxEvent;
import com.fee.app.schoolfeeapp.common.events.ParentInvitationEvent;
import com.fee.app.schoolfeeapp.common.events.StaffCreatedEvent;
import com.fee.app.schoolfeeapp.common.exceptions.SchoolFeeException;
import com.fee.app.schoolfeeapp.common.repository.OutboxEventRepository;
import com.fee.app.schoolfeeapp.fee.domain.FeeCategory;
import com.fee.app.schoolfeeapp.fee.repository.FeeCategoryRepository;
import com.fee.app.schoolfeeapp.notification.domain.NotificationTemplate;
import com.fee.app.schoolfeeapp.notification.repository.NotificationTemplateRepository;
import com.fee.app.schoolfeeapp.notification.service.EmailService;
import com.fee.app.schoolfeeapp.school.events.SchoolCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxEventProcessor Unit Tests")
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

    @Mock
    private EmailService emailService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private OutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OutboxEventProcessor(
                outboxRepository,
                invitationService,
                objectMapper,
                keycloakAdminService,
                userRepository,
                notificationTemplateRepository,
                feeCategoryRepository,
                emailService
        );

        // Catch-all to prevent NPEs during the onErrorResume pipeline execution
        lenient().when(outboxRepository.markAsFailed(any(), anyString(), anyInt(), any(), any()))
                .thenReturn(Mono.empty());

        // CRITICAL FIX: Prevent Mono.then(null) NPEs caused by Reactor's eager evaluation
        // calling unmocked methods during pipeline assembly.
        lenient().when(outboxRepository.markAsCompleted(any(UUID.class), any(Instant.class)))
                .thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("processPendingEvents - Event Claiming")
    class ProcessPendingEventsTests {

        // ========================================================================
        // Helper Methods
        // ========================================================================

        private OutboxEvent createOutboxEvent(UUID id, String eventType) {
            return OutboxEvent.builder()
                    .id(id)
                    .eventType(eventType)
                    .aggregateId(UUID.randomUUID())
                    .aggregateType("TEST")
                    .payload(objectMapper.createObjectNode())
                    .status("PROCESSING")
                    .retryCount(0)
                    .maxRetries(3)
                    .nextRetryAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
        }

        @Test
        @DisplayName("should process claimed pending events successfully")
        void shouldProcessClaimedPendingEvents() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID guardianId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ParentInvitationEvent payload = ParentInvitationEvent.builder()
                    .guardianId(guardianId)
                    .userId(userId)
                    .build();

            OutboxEvent event = createOutboxEventWithPayload(eventId, "PARENT_INVITATION", payload);

            when(invitationService.inviteGuardian(guardianId))
                    .thenReturn(Mono.empty());
            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(invitationService).inviteGuardian(guardianId);
            verify(outboxRepository).markAsCompleted(eq(eventId), any(Instant.class));
        }

        @Test
        @DisplayName("should handle empty event queue gracefully")
        void shouldHandleEmptyEventQueue() {
            // Arrange
            when(outboxRepository.claimPendingEvents(any(Instant.class), eq(50)))
                    .thenReturn(Flux.empty());

            // Act
            processor.processPendingEvents();

            // Assert
            verify(outboxRepository).claimPendingEvents(any(Instant.class), eq(50));
            verifyNoMoreInteractions(invitationService, keycloakAdminService, userRepository);
        }

        @Test
        @DisplayName("should continue processing when one event fails")
        void shouldContinueProcessingWhenOneEventFails() throws Exception {
            // Arrange
            UUID event1Id = UUID.randomUUID();
            UUID event2Id = UUID.randomUUID();
            UUID guardianId1 = UUID.randomUUID();
            UUID guardianId2 = UUID.randomUUID();

            // 1. Mock the payloads to bypass Jackson deserialization issues
            ParentInvitationEvent mockPayload1 = mock(ParentInvitationEvent.class);
            lenient().when(mockPayload1.getGuardianId()).thenReturn(guardianId1);
            lenient().when(mockPayload1.getUserId()).thenReturn(UUID.randomUUID());

            ParentInvitationEvent mockPayload2 = mock(ParentInvitationEvent.class);
            lenient().when(mockPayload2.getGuardianId()).thenReturn(guardianId2);
            lenient().when(mockPayload2.getUserId()).thenReturn(UUID.randomUUID());

            OutboxEvent event1 = createOutboxEvent(event1Id, "PARENT_INVITATION");
            OutboxEvent event2 = createOutboxEvent(event2Id, "PARENT_INVITATION");
            event1.setRetryCount(0);

            // Instruct the Spy to return payload1 on the first call, and payload2 on the second
            doReturn(mockPayload1).doReturn(mockPayload2)
                    .when(objectMapper).treeToValue(any(JsonNode.class), eq(ParentInvitationEvent.class));

            when(invitationService.inviteGuardian(guardianId1))
                    .thenReturn(Mono.error(new RuntimeException("Temporary failure")));
            when(invitationService.inviteGuardian(guardianId2))
                    .thenReturn(Mono.empty());

            // 2. CRITICAL FIX: Use lenient() and any() because markAsCompleted is invoked EAGERLY
            // for BOTH events during pipeline assembly, even though event1 errors out at runtime.
            lenient().when(outboxRepository.markAsCompleted(any(UUID.class), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result1 = invokeProcessClaimedEvent(event1);
            Mono<String> result2 = invokeProcessClaimedEvent(event2);

            // Assert
            StepVerifier.create(result1)
                    .expectNext(event1Id.toString())
                    .verifyComplete();

            StepVerifier.create(result2)
                    .expectNext(event2Id.toString())
                    .verifyComplete();

            verify(invitationService).inviteGuardian(guardianId1);
            verify(invitationService).inviteGuardian(guardianId2);
            verify(outboxRepository).markAsFailed(eq(event1Id), eq("PENDING"), eq(1), any(Instant.class), anyString());

            // We can still explicitly verify that markAsCompleted was ONLY successfully subscribed/invoked for event2
            verify(outboxRepository).markAsCompleted(eq(event2Id), any(Instant.class));
        }
    }

    @Nested
    @DisplayName("handleParentInvitation - Parent Invitation Processing")
    class HandleParentInvitationTests {

        @Test
        @DisplayName("should successfully process parent invitation event")
        void shouldSuccessfullyProcessParentInvitation() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID guardianId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ParentInvitationEvent payload = ParentInvitationEvent.builder()
                    .guardianId(guardianId)
                    .userId(userId)
                    .build();

            OutboxEvent event = createOutboxEventWithPayload(eventId, "PARENT_INVITATION", payload);

            when(invitationService.inviteGuardian(guardianId))
                    .thenReturn(Mono.empty());
            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(invitationService).inviteGuardian(guardianId);
            verify(outboxRepository).markAsCompleted(eq(eventId), any(Instant.class));
        }

        @Test
        @DisplayName("should handle invitation service failure with retry")
        void shouldHandleInvitationServiceFailureWithRetry() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID guardianId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ParentInvitationEvent payload = ParentInvitationEvent.builder()
                    .guardianId(guardianId)
                    .userId(userId)
                    .build();

            OutboxEvent event = createOutboxEventWithPayload(eventId, "PARENT_INVITATION", payload);
            event.setRetryCount(0);

            when(invitationService.inviteGuardian(guardianId))
                    .thenReturn(Mono.error(new RuntimeException("SMS gateway timeout")));

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(invitationService).inviteGuardian(guardianId);
            verify(outboxRepository).markAsFailed(
                    eq(eventId),
                    eq("PENDING"),
                    eq(1),
                    any(Instant.class),
                    contains("SMS gateway timeout")
            );
        }

        @Test
        @DisplayName("should mark as permanently failed after max retries")
        void shouldMarkAsPermanentlyFailedAfterMaxRetries() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID guardianId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            ParentInvitationEvent payload = ParentInvitationEvent.builder()
                    .guardianId(guardianId)
                    .userId(userId)
                    .build();

            OutboxEvent event = createOutboxEventWithPayload(eventId, "PARENT_INVITATION", payload);
            event.setRetryCount(3); // Already at max retries

            when(invitationService.inviteGuardian(guardianId))
                    .thenReturn(Mono.error(new RuntimeException("Persistent failure")));

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(outboxRepository).markAsFailed(
                    eq(eventId),
                    eq("FAILED"),
                    eq(4),
                    any(Instant.class),
                    contains("Persistent failure")
            );
        }

        @Test
        @DisplayName("should handle invalid JSON payload gracefully")
        void shouldHandleInvalidJsonPayload() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            OutboxEvent event = OutboxEvent.builder()
                    .id(eventId)
                    .eventType("PARENT_INVITATION")
                    .payload(objectMapper.createObjectNode().put("invalidField", "value"))
                    .retryCount(0)
                    .build();

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(outboxRepository).markAsFailed(eq(eventId), eq("PENDING"), eq(1), any(Instant.class), anyString());
        }
    }

    @Nested
    @DisplayName("handleStaffCreated - Staff Creation Processing")
    class HandleStaffCreatedTests {

        @Test
        @DisplayName("should successfully create staff user in Keycloak and send credentials")
        void shouldSuccessfullyCreateStaffUser() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID keycloakUserId = UUID.randomUUID();
            UUID schoolId = UUID.randomUUID();

            StaffCreatedEvent payload = StaffCreatedEvent.builder()
                    .userId(userId)
                    .email("teacher@school.com")
                    .phoneNumber("+2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("TEACHER")
                    .roles(Set.of("teacher"))
                    .schoolId(schoolId)
                    .schoolName("Test School")
                    .build();

            OutboxEvent event = createOutboxEventWithPayload(eventId, "STAFF_CREATED", payload);

            // Mock User to prevent NPE in the doOnSuccess block
            User mockUser = mock(User.class);
            lenient().when(mockUser.getId()).thenReturn(userId);
            lenient().when(mockUser.getKeycloakId()).thenReturn(keycloakUserId);

            when(keycloakAdminService.createUser(any(UserRepresentation.class), eq("TEACHER"), eq(Set.of("teacher"))))
                    .thenReturn(Mono.just(new KeycloakUserResult(keycloakUserId, "tempPassword")));
            when(userRepository.updateKeycloakId(userId, keycloakUserId))
                    .thenReturn(Mono.just(mockUser));
            when(emailService.sendStaffWelcomeEmail("teacher@school.com", "Test School", "tempPassword"))
                    .thenReturn(Mono.empty());
            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(keycloakAdminService).createUser(any(UserRepresentation.class), eq("TEACHER"), eq(Set.of("teacher")));
            verify(userRepository).updateKeycloakId(userId, keycloakUserId);
            verify(emailService).sendStaffWelcomeEmail("teacher@school.com", "Test School", "tempPassword");
            verify(outboxRepository).markAsCompleted(eq(eventId), any(Instant.class));
        }

        @Test
        @DisplayName("should handle Keycloak user creation failure")
        void shouldHandleKeycloakUserCreationFailure() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID schoolId = UUID.randomUUID();

            StaffCreatedEvent payload = StaffCreatedEvent.builder()
                    .userId(userId)
                    .email("teacher@school.com")
                    .phoneNumber("+2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("TEACHER")
                    .roles(Set.of("teacher"))
                    .schoolId(schoolId)
                    .schoolName("Test School")
                    .build();

            OutboxEvent event = createOutboxEventWithPayload(eventId, "STAFF_CREATED", payload);
            event.setRetryCount(0);

            when(keycloakAdminService.createUser(any(UserRepresentation.class), anyString(), anySet()))
                    .thenReturn(Mono.error(new SchoolFeeException("KEYCLOAK_ERROR", "Connection timeout")));

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(keycloakAdminService).createUser(any(UserRepresentation.class), anyString(), anySet());
            verify(outboxRepository).markAsFailed(
                    eq(eventId),
                    eq("PENDING"),
                    eq(1),
                    any(Instant.class),
                    contains("Connection timeout")
            );
        }

        @Test
        @DisplayName("should handle database update failure after Keycloak creation")
        void shouldHandleDatabaseUpdateFailureAfterKeycloakCreation() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UUID keycloakUserId = UUID.randomUUID();
            UUID schoolId = UUID.randomUUID();

            StaffCreatedEvent payload = StaffCreatedEvent.builder()
                    .userId(userId)
                    .email("teacher@school.com")
                    .phoneNumber("+2348012345678")
                    .firstName("John")
                    .lastName("Doe")
                    .userType("TEACHER")
                    .roles(Set.of("teacher"))
                    .schoolId(schoolId)
                    .schoolName("Test School")
                    .build();

            OutboxEvent event = createOutboxEventWithPayload(eventId, "STAFF_CREATED", payload);
            event.setRetryCount(0);

            when(keycloakAdminService.createUser(any(UserRepresentation.class), anyString(), anySet()))
                    .thenReturn(Mono.just(new KeycloakUserResult(keycloakUserId, "tempPassword")));
            when(userRepository.updateKeycloakId(userId, keycloakUserId))
                    .thenReturn(Mono.error(new RuntimeException("Database connection lost")));

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(keycloakAdminService).createUser(any(UserRepresentation.class), anyString(), anySet());
            verify(userRepository).updateKeycloakId(userId, keycloakUserId);
        }
    }

    @Nested
    @DisplayName("handleKeycloakCleanup - Orphaned User Cleanup")
    class HandleKeycloakCleanupTests {

        @Test
        @DisplayName("should successfully delete orphaned Keycloak user")
        void shouldSuccessfullyDeleteOrphanedKeycloakUser() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID keycloakUserId = UUID.randomUUID();
            String reason = "DB transaction rollback";

            JsonNode payload = objectMapper.createObjectNode()
                    .put("keycloakUserId", keycloakUserId.toString())
                    .put("reason", reason);

            OutboxEvent event = OutboxEvent.builder()
                    .id(eventId)
                    .eventType("KEYCLOAK_CLEANUP")
                    .payload(payload)
                    .retryCount(0)
                    .build();

            doNothing().when(keycloakAdminService).deleteUser(keycloakUserId.toString());
            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(keycloakAdminService).deleteUser(keycloakUserId.toString());
            verify(outboxRepository).markAsCompleted(eq(eventId), any(Instant.class));
        }

        @Test
        @DisplayName("should handle Keycloak deletion failure")
        void shouldHandleKeycloakDeletionFailure() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID keycloakUserId = UUID.randomUUID();
            String reason = "DB transaction rollback";

            JsonNode payload = objectMapper.createObjectNode()
                    .put("keycloakUserId", keycloakUserId.toString())
                    .put("reason", reason);

            OutboxEvent event = OutboxEvent.builder()
                    .id(eventId)
                    .eventType("KEYCLOAK_CLEANUP")
                    .payload(payload)
                    .retryCount(0)
                    .build();

            doThrow(new RuntimeException("Keycloak API error"))
                    .when(keycloakAdminService).deleteUser(keycloakUserId.toString());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(keycloakAdminService).deleteUser(keycloakUserId.toString());
        }
    }

    @Nested
    @DisplayName("handleSchoolCreated - School Setup Processing")
    class HandleSchoolCreatedTests {

        @Test
        @DisplayName("should successfully setup school with admin welcome email, templates, and fee categories")
        void shouldSuccessfullySetupSchool() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID schoolId = UUID.randomUUID();
            UUID adminKeycloakId = UUID.randomUUID();

            SchoolCreatedEvent payload = new SchoolCreatedEvent(
                    schoolId,
                    "Test School",
                    "TS001",
                    adminKeycloakId,
                    "tempPassword",
                    "admin@school.com"
            );

            OutboxEvent event = createOutboxEventWithPayload(eventId, "SCHOOL_CREATED", payload);

            when(emailService.sendAdminWelcomeEmail("admin@school.com", "Test School", "tempPassword")).thenReturn(Mono.empty());

            when(notificationTemplateRepository.existsBySchoolIdAndTemplateCodeAndChannel(any(), anyString(), anyString()))
                    .thenReturn(Mono.just(false));
            when(notificationTemplateRepository.save(any(NotificationTemplate.class)))
                    .thenReturn(Mono.empty());

            when(feeCategoryRepository.existsBySchoolIdAndName(any(), anyString()))
                    .thenReturn(Mono.just(false));
            when(feeCategoryRepository.save(any(FeeCategory.class)))
                    .thenReturn(Mono.empty());

            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(emailService).sendAdminWelcomeEmail("admin@school.com", "Test School", "tempPassword");
            verify(notificationTemplateRepository, times(4)).save(any(NotificationTemplate.class));
            verify(feeCategoryRepository, times(8)).save(any(FeeCategory.class));
            verify(outboxRepository).markAsCompleted(eq(eventId), any(Instant.class));
        }

        @Test
        @DisplayName("should skip admin email when adminKeycloakId is null")
        void shouldSkipAdminEmailWhenAdminKeycloakIdIsNull() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID schoolId = UUID.randomUUID();

            SchoolCreatedEvent payload = new SchoolCreatedEvent(
                    schoolId,
                    "Test School",
                    "TS001",
                    null,
                    null,
                    null
            );

            OutboxEvent event = createOutboxEventWithPayload(eventId, "SCHOOL_CREATED", payload);

            when(notificationTemplateRepository.existsBySchoolIdAndTemplateCodeAndChannel(any(), anyString(), anyString()))
                    .thenReturn(Mono.just(false));
            when(notificationTemplateRepository.save(any(NotificationTemplate.class)))
                    .thenReturn(Mono.empty());

            when(feeCategoryRepository.existsBySchoolIdAndName(any(), anyString()))
                    .thenReturn(Mono.just(false));
            when(feeCategoryRepository.save(any(FeeCategory.class)))
                    .thenReturn(Mono.empty());

            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(emailService, never()).sendAdminWelcomeEmail(anyString(), anyString(), anyString());
            verify(notificationTemplateRepository, times(4)).save(any(NotificationTemplate.class));
            verify(feeCategoryRepository, times(8)).save(any(FeeCategory.class));
        }

        @Test
        @DisplayName("should not create duplicate templates or fee categories")
        void shouldNotCreateDuplicateTemplatesOrFeeCategories() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID schoolId = UUID.randomUUID();
            UUID adminKeycloakId = UUID.randomUUID();

            SchoolCreatedEvent payload = new SchoolCreatedEvent(
                    schoolId,
                    "Test School",
                    "TS001",
                    adminKeycloakId,
                    "tempPassword",
                    "admin@school.com"
            );

            OutboxEvent event = createOutboxEventWithPayload(eventId, "SCHOOL_CREATED", payload);

            when(emailService.sendAdminWelcomeEmail("admin@school.com", "Test School", "tempPassword")).thenReturn(Mono.empty());

            when(notificationTemplateRepository.existsBySchoolIdAndTemplateCodeAndChannel(any(), anyString(), anyString()))
                    .thenReturn(Mono.just(true));

            when(feeCategoryRepository.existsBySchoolIdAndName(any(), anyString()))
                    .thenReturn(Mono.just(true));

            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(emailService).sendAdminWelcomeEmail("admin@school.com", "Test School", "tempPassword");
            verify(notificationTemplateRepository, never()).save(any(NotificationTemplate.class));
            verify(feeCategoryRepository, never()).save(any(FeeCategory.class));
        }

        @Test
        @DisplayName("should handle concurrent template creation gracefully")
        void shouldHandleConcurrentTemplateCreationGracefully() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            UUID schoolId = UUID.randomUUID();
            UUID adminKeycloakId = UUID.randomUUID();

            SchoolCreatedEvent payload = new SchoolCreatedEvent(
                    schoolId,
                    "Test School",
                    "TS001",
                    adminKeycloakId,
                    "tempPassword",
                    "admin@school.com"
            );

            OutboxEvent event = createOutboxEventWithPayload(eventId, "SCHOOL_CREATED", payload);

            when(emailService.sendAdminWelcomeEmail("admin@school.com", "Test School", "tempPassword")).thenReturn(Mono.empty());

            when(notificationTemplateRepository.existsBySchoolIdAndTemplateCodeAndChannel(any(), anyString(), anyString()))
                    .thenReturn(Mono.just(false));
            when(notificationTemplateRepository.save(any(NotificationTemplate.class)))
                    .thenReturn(Mono.error(new org.springframework.dao.DuplicateKeyException("Concurrent insert")));

            when(feeCategoryRepository.existsBySchoolIdAndName(any(), anyString()))
                    .thenReturn(Mono.just(false));
            when(feeCategoryRepository.save(any(FeeCategory.class)))
                    .thenReturn(Mono.empty());

            when(outboxRepository.markAsCompleted(eq(eventId), any(Instant.class)))
                    .thenReturn(Mono.empty());

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(notificationTemplateRepository, times(4)).save(any(NotificationTemplate.class));
        }
    }

    @Nested
    @DisplayName("Unknown Event Type Handling")
    class UnknownEventTypeTests {

        @Test
        @DisplayName("should reject unknown event types")
        void shouldRejectUnknownEventTypes() {
            // Arrange
            UUID eventId = UUID.randomUUID();
            OutboxEvent event = OutboxEvent.builder()
                    .id(eventId)
                    .eventType("UNKNOWN_TYPE")
                    .payload(objectMapper.createObjectNode())
                    .retryCount(0)
                    .build();

            // Act
            Mono<String> result = invokeProcessClaimedEvent(event);

            // Assert
            StepVerifier.create(result)
                    .expectNext(eventId.toString())
                    .verifyComplete();

            verify(outboxRepository).markAsFailed(eq(eventId), eq("PENDING"), eq(1), any(Instant.class), anyString());
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    private <T> OutboxEvent createOutboxEventWithPayload(UUID id, String eventType, T payloadObject) {
        try {
            JsonNode payload = objectMapper.valueToTree(payloadObject);
            return OutboxEvent.builder()
                    .id(id)
                    .eventType(eventType)
                    .aggregateId(UUID.randomUUID())
                    .aggregateType("TEST")
                    .payload(payload)
                    .status("PROCESSING")
                    .retryCount(0)
                    .maxRetries(3)
                    .nextRetryAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Mono<String> invokeProcessClaimedEvent(OutboxEvent event) {
        try {
            var method = OutboxEventProcessor.class.getDeclaredMethod("processClaimedEvent", OutboxEvent.class);
            method.setAccessible(true);
            return (Mono<String>) method.invoke(processor, event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke processClaimedEvent", e);
        }
    }
}