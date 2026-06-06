package com.fee.app.schoolfeeapp.common.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.fee.app.schoolfeeapp.school.events.SchoolCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final OutboxEventRepository outboxRepository;
    private final GuardianInvitationService invitationService;
    private final ObjectMapper objectMapper;
    private final KeycloakAdminServiceImpl keycloakAdminService;
    private final UserRepository userRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final FeeCategoryRepository feeCategoryRepository;

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 3;

    /**
     * Process pending outbox events every 5 seconds.
     * ATOMIC CLAIM PATTERN:
     * - Uses FOR UPDATE SKIP LOCKED to prevent race conditions
     * - Multiple pods can run this simultaneously without conflicts
     * - Each event is processed by exactly ONE pod
     */
    @Scheduled(fixedDelay = 5000)
    public void processPendingEvents() {
        log.debug("Starting outbox event processing...");

        outboxRepository.claimPendingEvents(Instant.now(), BATCH_SIZE)
                .flatMap(this::processClaimedEvent)
                .onErrorContinue((error, obj) ->
                        log.error("Failed processing event {}", obj, error))
                .subscribe();
    }


    /**
     * Process an event that has already been atomically claimed.
     * No need for markAsProcessing() - it's already in PROCESSING state.
     */
    private Mono<String> processClaimedEvent(OutboxEvent event) {
        log.info("Processing claimed event: type={}, id={}",
                event.getEventType(), event.getId());

        Mono<Void> processingWork = switch (event.getEventType()) {
            case "PARENT_INVITATION" -> handleParentInvitation(event);
            case "STAFF_CREATED" -> handleStaffCreated(event);
            case "KEYCLOAK_CLEANUP" -> handleKeycloakCleanup(event);
            case "SCHOOL_CREATED" -> handleSchoolCreated(event);

            default -> {
                log.warn("Unknown event type: {}", event.getEventType());
                yield Mono.error(new IllegalArgumentException("Unknown event type: " + event.getEventType()));
            }
        };

        return processingWork
                .then(outboxRepository.markAsCompleted(event.getId(), Instant.now()))
                .doOnSuccess(v -> log.info("Event processed successfully: id={}, type={}", event.getId(), event.getEventType()))
                .onErrorResume(error -> handleProcessingError(event, error).then())
                .thenReturn(event.getId().toString());
    }

    /**
     * Handle parent invitation event.
     * Sends SMS invitation to guardian.
     */
    private Mono<Void> handleParentInvitation(OutboxEvent event) {
        try {
            ParentInvitationEvent payload = objectMapper.treeToValue(
                    event.getPayload(),
                    ParentInvitationEvent.class);

            UUID guardianId = payload.getGuardianId();
            UUID userId = payload.getUserId();

            log.info("Sending parent invitation: guardianId={}, userId={}", guardianId, userId);

            return invitationService.inviteGuardian(guardianId)
                    .doOnSuccess(v -> log.info("Parent invitation sent successfully: guardianId={}", guardianId))
                    .doOnError(e -> log.error("Failed to send parent invitation: guardianId={}", guardianId, e)).then();

        } catch (Exception e) {
            log.error("Failed to parse parent invitation payload: eventId={}", event.getId(), e);
            return Mono.error(new RuntimeException("Failed to parse payload", e));
        }
    }

    /**
     * Handle Keycloak cleanup event.
     * Deletes orphaned Keycloak users when DB transaction fails.
     */
    private Mono<Void> handleKeycloakCleanup(OutboxEvent event) {
        try {
            JsonNode payload = event.getPayload();
            String keycloakUserIdStr = payload.get("keycloakUserId").asText();
            String reason = payload.get("reason").asText();

            UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);

            log.warn("Cleaning up orphaned Keycloak user: keycloakId={}, reason={}",
                    keycloakUserId, reason);

      return Mono.fromRunnable(
          () -> {
            try {
              keycloakAdminService.deleteUser(keycloakUserIdStr);
              log.info(
                  "Orphaned Keycloak user deleted successfully: keycloakId={}", keycloakUserId);
            } catch (Exception e) {
              log.error(
                  "Failed to delete orphaned Keycloak user: keycloakId={}", keycloakUserId, e);
              throw new SchoolFeeException("Failed to delete Keycloak user", e);
            }
          });

        } catch (Exception e) {
            log.error("Failed to parse Keycloak cleanup payload: eventId={}", event.getId(), e);
      return Mono.error(new SchoolFeeException("Failed to parse payload", e));
        }
    }
    /**
     * Handle staff creation event.
     * Creates Keycloak user and sends credentials email.
     */
    private Mono<Void> handleStaffCreated(OutboxEvent event) {
        try {
            StaffCreatedEvent payload = objectMapper.treeToValue(
                    event.getPayload(),
                    StaffCreatedEvent.class);

            log.info("Creating Keycloak user for staff: userId={}, email={}",
                    payload.getUserId(), payload.getEmail());

            // Step 1: Create Keycloak user
            return createKeycloakStaffUser(payload)
                    .flatMap(keycloakUserId -> {
                        log.info("Keycloak user created: keycloakId={}", keycloakUserId);

                        // Step 2: Update local user with Keycloak ID
                        return userRepository.updateKeycloakId(payload.getUserId(), keycloakUserId)
                                .doOnSuccess(updatedUser ->
                                        log.info("Local user updated with Keycloak ID: userId={}, keycloakId={}",
                                                updatedUser.getId(), updatedUser.getKeycloakId()))
                                .then(sendCredentialsEmail(keycloakUserId, payload));
                    })
                    .doOnSuccess(v -> log.info("Staff account setup completed: userId={}", payload.getUserId()))
                    .doOnError(e -> log.error("Failed to setup staff account: userId={}", payload.getUserId(), e));

        } catch (Exception e) {
            log.error("Failed to parse staff creation payload: eventId={}", event.getId(), e);
            return Mono.error(new RuntimeException("Failed to parse payload", e));
        }
    }

    /**
     * Send credentials email via Keycloak.
     */
    private Mono<Void> sendCredentialsEmail(UUID keycloakUserId, StaffCreatedEvent payload) {
                    return Mono.fromCallable(() -> {
                                String keycloakIdStr = keycloakUserId.toString();
                                keycloakAdminService.sendPasswordResetEmail(keycloakIdStr);
                                log.info("Credentials email sent to: {}", payload.getEmail());
                                return keycloakIdStr;
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnError(ex -> log.error("Failed to send credentials email to: {}", payload.getEmail(), ex))
                            .onErrorMap(ex -> new SchoolFeeException("Failed to send credentials email", ex))
                            .then();
    }

    /**
     * Create staff user in Keycloak.
     */
    private Mono<UUID> createKeycloakStaffUser(StaffCreatedEvent payload) {
        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(payload.getEmail());
        kcUser.setEmail(payload.getEmail());
        kcUser.setFirstName(payload.getFirstName());
        kcUser.setLastName(payload.getLastName());
        kcUser.setEnabled(true);

        // Set attributes
        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("phone", List.of(payload.getPhoneNumber()));
        attributes.put("user_type", List.of(payload.getUserType()));
        attributes.put("school_id", List.of(payload.getSchoolId().toString()));
        attributes.put("school_name", List.of(payload.getSchoolName() != null ? payload.getSchoolName() : ""));
        kcUser.setAttributes(attributes);

        return keycloakAdminService.createUser(kcUser, payload.getUserType(), payload.getRoles());
    }

    /**
     * Handle processing errors with retry logic.
     */
    private Mono<String> handleProcessingError(OutboxEvent event, Throwable error) {
        int newRetryCount = event.getRetryCount() + 1;

        if (newRetryCount >= MAX_RETRIES) {
            // Max retries exceeded - mark as permanently failed
            log.error("Event permanently failed after {} retries: eventId={}, type={}, lastError={}",
                    MAX_RETRIES, event.getId(), event.getEventType(), error.getMessage());

            return outboxRepository.markAsFailed(
                            event.getId(),
                            "FAILED",
                            newRetryCount,
                            Instant.now(),
                            error.getMessage()
                    )
                    .then(Mono.just("PERMANENTLY_FAILED"));
        } else {
            // Schedule retry with exponential backoff
            long backoffSeconds = (long) Math.pow(2, newRetryCount) * 60; // 2min, 4min, 8min
            Instant nextRetry = Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS);

            log.warn("Event processing failed, scheduling retry {}/{}: eventId={}, nextRetryAt={}",
                    newRetryCount, MAX_RETRIES, event.getId(), nextRetry);

            return outboxRepository.markAsFailed(
                            event.getId(),
                            "PENDING",
                            newRetryCount,
                            nextRetry,
                            error.getMessage()
                    )
                    .then(Mono.just("RETRY_SCHEDULED"));
        }
    }

  /**
   * Handle school created event.
   * Performs async setup tasks:
   * - Welcome email/password action to admin (if Keycloak user was created)
   * - Setup default notification templates
   * - Create default fee categories
   */
  private Mono<Void> handleSchoolCreated(OutboxEvent event) {
    try {
      SchoolCreatedEvent payload =
          objectMapper.treeToValue(event.getPayload(), SchoolCreatedEvent.class);

      log.info(
          "Processing school created event: schoolId={}, schoolName={}, adminKeycloakId={}",
          payload.schoolId(),
          payload.schoolName(),
          payload.adminKeycloakId());

      return sendAdminWelcomeEmail(payload)
          .then(createDefaultNotificationTemplates(payload.schoolId()))
          .then(createDefaultFeeCategories(payload.schoolId()))
          .doOnSuccess(
              v ->
                  log.info(
                      "School '{}' setup completed. Admin: {}",
                      payload.schoolName(),
                      payload.adminKeycloakId() != null
                          ? payload.adminKeycloakId()
                          : "MANUAL_SETUP_REQUIRED"));
    } catch (Exception e) {
      log.error("Failed to parse school created payload: eventId={}", event.getId(), e);
      return Mono.error(
          new SchoolFeeException(
              "SCHOOL_CREATED_PAYLOAD_PARSE_FAILED",
              "Failed to parse school created payload",
              "payload",
              e));
    }
  }

  private Mono<Void> sendAdminWelcomeEmail(SchoolCreatedEvent payload) {
    if (payload.adminKeycloakId() == null) {
      log.warn(
          "Skipping admin welcome email because no admin Keycloak user exists yet: schoolId={}",
          payload.schoolId());
      return Mono.empty();
    }

    return Mono.fromRunnable(
            () -> keycloakAdminService.sendPasswordResetEmail(payload.adminKeycloakId().toString()))
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess(
            v ->
                log.info(
                    "Admin welcome/password email sent: schoolId={}, adminKeycloakId={}",
                    payload.schoolId(),
                    payload.adminKeycloakId()))
        .doOnError(
            error ->
                log.error(
                    "Failed to send admin welcome/password email: schoolId={}, adminKeycloakId={}",
                    payload.schoolId(),
                    payload.adminKeycloakId(),
                    error))
        .onErrorMap(error -> new SchoolFeeException("Failed to send admin welcome email", error))
        .then();
  }

  private Mono<Void> createDefaultNotificationTemplates(UUID schoolId) {
    return Flux.fromIterable(defaultNotificationTemplates(schoolId))
        .concatMap(this::saveTemplateIfMissing)
        .filter(Boolean.TRUE::equals)
        .count()
        .doOnNext(
            createdCount ->
                log.info(
                    "Default notification templates ensured: schoolId={}, created={}",
                    schoolId,
                    createdCount))
        .then();
  }

  private Mono<Boolean> saveTemplateIfMissing(NotificationTemplate template) {
    return notificationTemplateRepository
        .existsBySchoolIdAndTemplateCodeAndChannel(
            template.getSchoolId(), template.getTemplateCode(), template.getChannel())
        .flatMap(
            exists -> {
              if (Boolean.TRUE.equals(exists)) {
                log.debug(
                    "Default notification template already exists: schoolId={}, templateCode={}, channel={}",
                    template.getSchoolId(),
                    template.getTemplateCode(),
                    template.getChannel());
                return Mono.just(false);
              }
              return notificationTemplateRepository.save(template).thenReturn(true);
            })
        .onErrorResume(
            DuplicateKeyException.class,
            error -> {
              log.debug(
                  "Default notification template was created concurrently: schoolId={}, templateCode={}, channel={}",
                  template.getSchoolId(),
                  template.getTemplateCode(),
                  template.getChannel());
              return Mono.just(false);
            });
  }

  private Mono<Void> createDefaultFeeCategories(UUID schoolId) {
    return Flux.fromIterable(defaultFeeCategories(schoolId))
        .concatMap(this::saveCategoryIfMissing)
        .filter(Boolean.TRUE::equals)
        .count()
        .doOnNext(
            createdCount ->
                log.info(
                    "Default fee categories ensured: schoolId={}, created={}",
                    schoolId,
                    createdCount))
        .then();
  }

  private Mono<Boolean> saveCategoryIfMissing(FeeCategory category) {
    return feeCategoryRepository
        .existsBySchoolIdAndName(category.getSchoolId(), category.getName())
        .flatMap(
            exists -> {
              if (exists) {
                log.debug(
                    "Default fee category already exists: schoolId={}, name={}",
                    category.getSchoolId(),
                    category.getName());
                return Mono.just(false);
              }
              return feeCategoryRepository.save(category).thenReturn(true);
            })
        .onErrorResume(
            DuplicateKeyException.class,
            error -> {
              log.debug(
                  "Default fee category was created concurrently: schoolId={}, name={}",
                  category.getSchoolId(),
                  category.getName());
              return Mono.just(false);
            });
  }

  private List<NotificationTemplate> defaultNotificationTemplates(UUID schoolId) {
    Instant now = Instant.now();

    return List.of(
        defaultSmsTemplate(
            schoolId,
            "FEE_DUE_REMINDER",
            "Fee Due Reminder",
            "Hello {{guardianName}}, {{studentName}}'s {{termName}} fee of {{amountDue}} at {{schoolName}} is due on {{dueDate}}. Balance: {{balance}}.",
            now,
            "guardianName",
            "studentName",
            "termName",
            "amountDue",
            "schoolName",
            "dueDate",
            "balance"),
        defaultSmsTemplate(
            schoolId,
            "FEE_OVERDUE_NOTICE",
            "Fee Overdue Notice",
            "Hello {{guardianName}}, {{studentName}} has an overdue fee balance of {{balance}} at {{schoolName}}. Please pay as soon as possible.",
            now,
            "guardianName",
            "studentName",
            "balance",
            "schoolName"),
        defaultSmsTemplate(
            schoolId,
            "PAYMENT_RECEIPT",
            "Payment Receipt",
            "{{schoolName}} received {{amountPaid}} for {{studentName}} on {{paymentDate}}. Receipt: {{receiptNumber}}. Balance: {{balance}}.",
            now,
            "schoolName",
            "amountPaid",
            "studentName",
            "paymentDate",
            "receiptNumber",
            "balance"),
        defaultSmsTemplate(
            schoolId,
            "PARENT_INVITATION",
            "Parent Portal Invitation",
            "Hello {{guardianName}}, {{schoolName}} invited you to access {{studentName}}'s fee account. Use this link to continue: {{inviteLink}}",
            now,
            "guardianName",
            "schoolName",
            "studentName",
            "inviteLink"));
  }

  private NotificationTemplate defaultSmsTemplate(
      UUID schoolId,
      String templateCode,
      String name,
      String bodyTemplate,
      Instant createdAt,
      String... variables) {
    return NotificationTemplate.builder()
        .id(UUID.randomUUID())
        .schoolId(schoolId)
        .templateCode(templateCode)
        .name(name)
        .channel("SMS")
        .subject(null)
        .bodyTemplate(bodyTemplate)
        .variables(templateVariables(variables))
        .isDefault(true)
        .createdAt(createdAt)
        .build();
  }

  private JsonNode templateVariables(String... variableNames) {
    var node = objectMapper.createObjectNode();
    var variables = node.putArray("variables");
    for (String variableName : variableNames) {
      variables.add(variableName);
    }
    return node;
  }

  private List<FeeCategory> defaultFeeCategories(UUID schoolId) {
    Instant now = Instant.now();
    return List.of(
        defaultFeeCategory(
            schoolId, "Tuition", "Core academic tuition fees.", true, false, now),
        defaultFeeCategory(
            schoolId, "Registration", "Admission, registration, and enrollment fees.", false, false, now),
        defaultFeeCategory(
            schoolId, "Examination", "Assessment and examination fees.", true, false, now),
        defaultFeeCategory(
            schoolId, "Books and Supplies", "Textbooks, notebooks, and learning materials.", false, true, now),
        defaultFeeCategory(
            schoolId, "Uniform", "School uniforms and approved clothing items.", false, true, now),
        defaultFeeCategory(
            schoolId, "Transport", "School transport or bus service fees.", true, true, now),
        defaultFeeCategory(
            schoolId, "Feeding", "Meal plan and feeding fees.", true, true, now),
        defaultFeeCategory(
            schoolId, "Boarding", "Hostel or boarding house fees.", true, true, now));
  }

  private FeeCategory defaultFeeCategory(
      UUID schoolId,
      String name,
      String description,
      boolean isRecurring,
      boolean isOptional,
      Instant createdAt) {
    return FeeCategory.builder()
        .id(UUID.randomUUID())
        .schoolId(schoolId)
        .name(name)
        .description(description)
        .isRecurring(isRecurring)
        .isOptional(isOptional)
        .createdAt(createdAt)
        .build();
  }
}
